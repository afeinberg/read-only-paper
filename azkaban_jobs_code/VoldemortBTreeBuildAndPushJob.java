package com.linkedin.batch.jobs;

import java.io.IOException;
import java.net.URI;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapred.JobConf;
import org.apache.log4j.Logger;

import voldemort.client.protocol.admin.AdminClient;
import voldemort.client.protocol.admin.AdminClientConfig;
import voldemort.cluster.Cluster;
import voldemort.serialization.SerializerDefinition;
import voldemort.serialization.json.JsonTypeDefinition;
import voldemort.store.StoreDefinition;
import voldemort.store.readonly.checksum.CheckSum;
import voldemort.store.readonly.checksum.CheckSum.CheckSumType;
import voldemort.utils.Utils;
import azkaban.common.jobs.AbstractJob;
import azkaban.common.utils.Props;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.linkedin.batch.jobs.VoldemortBTreeStoreBuilderJob.VoldemortStoreBuilderConf;
import com.linkedin.batch.jobs.VoldemortSwapJob.VoldemortSwapConf;
import com.linkedin.batch.utils.HadoopUtils;
import com.linkedin.batch.utils.JsonSchema;
import com.linkedin.batch.utils.VoldemortUtils;

public class VoldemortBTreeBuildAndPushJob extends AbstractJob
{
  private final Logger          log;

  private final Props           props;

  private Cluster               cluster;

  private List<StoreDefinition> storeDefs;

  private final String          storeName;

  private final List<String>    clusterUrl;

  private final int             nodeId;

  private final List<String>    dataDirs;

  public VoldemortBTreeBuildAndPushJob(String name, Props props)
  {
    super(name);
    this.props = props;
    this.storeName = props.getString("push.store.name").trim();
    this.clusterUrl = new ArrayList<String>();
    this.dataDirs = new ArrayList<String>();

    String clusterUrlText = props.getString("push.cluster");
    for (String url : Utils.COMMA_SEP.split(clusterUrlText.trim()))
      if (url.trim().length() > 0)
        this.clusterUrl.add(url);

    if (clusterUrl.size() <= 0)
      throw new RuntimeException("Number of urls should be atleast 1");

    // Support multiple output dirs if the user mentions only "push", no "build".
    // If user mentions both then should have only one
    String dataDirText = props.getString("build.output.dir");
    for (String dataDir : Utils.COMMA_SEP.split(dataDirText.trim()))
      if (dataDir.trim().length() > 0)
        this.dataDirs.add(dataDir);

    if (dataDirs.size() <= 0)
      throw new RuntimeException("Number of data dirs should be atleast 1");

    this.nodeId = props.getInt("push.node", 0);
    this.log = Logger.getLogger(name);
  }

  @Override
  public void run() throws Exception
  {
    boolean build = props.getBoolean("build", true);
    boolean push = props.getBoolean("push", true);

    if (build && push && dataDirs.size() != 1)
    {
      // Should have only one data directory ( which acts like the parent directory to all
      // urls )
      throw new RuntimeException(" Should have only one data directory ( which acts like root directory ) since they are auto-generated during build phase ");
    }
    else if (!build && push && dataDirs.size() != clusterUrl.size())
    {
      // Number of data directories should be equal to number of cluster urls
      throw new RuntimeException(" Since we are only pushing, number of data directories ( comma separated ) should be equal to number of cluster urls ");
    }

    // Check every url individually
    HashMap<String, Exception> exceptions = Maps.newHashMap();

    for (int index = 0; index < clusterUrl.size(); index++)
    {
      String url = clusterUrl.get(index);

      log.info("Working on " + url);

      try
      {
        verifySchema(url);

        String buildOutputDir;
        if (build)
        {
          buildOutputDir = runBuildStore(props, url);
        }
        else
        {
          buildOutputDir = dataDirs.get(index);
        }

        if (push)
        {
          runPushStore(props, url, buildOutputDir);
        }

        if (build && push && !props.getBoolean("build.output.keep", false))
        {
          JobConf jobConf = new JobConf();

          if (props.containsKey("hadoop.job.ugi"))
          {
            jobConf.set("hadoop.job.ugi", props.getString("hadoop.job.ugi"));
          }

          log.info("Deleting " + buildOutputDir);
          HadoopUtils.deletePathIfExists(jobConf, buildOutputDir);
          log.info("Deleted " + buildOutputDir);
        }
      }
      catch (Exception e)
      {
        log.error("Exception during build and push for url " + url, e);
        exceptions.put(url, e);
      }
    }

    if (exceptions.size() > 0)
    {
      throw new Exception("Got exceptions while pushing to "
          + Joiner.on(",").join(exceptions.keySet()) + " => "
          + Joiner.on(",").join(exceptions.values()));
    }
  }

  public void verifySchema(String url) throws Exception
  {
    // create new json store def with schema from the metadata in the input path
    JsonSchema schema = HadoopUtils.getSchemaFromPath(getInputPath());
    int replicationFactor = props.getInt("build.replication.factor", 2);
    int requiredReads = props.getInt("build.required.reads", 1);
    int requiredWrites = props.getInt("build.required.writes", 1);
    String description = props.getString("push.store.description", "");
    String owners = props.getString("push.store.owners", "");
    String keySchema =
        "\n\t\t<type>json</type>\n\t\t<schema-info version=\"0\">" + schema.getKeyType()
            + "</schema-info>\n\t";
    String valSchema =
        "\n\t\t<type>json</type>\n\t\t<schema-info version=\"0\">"
            + schema.getValueType() + "</schema-info>\n\t";

    if (props.containsKey("build.compress.key"))
    {
      keySchema += "\t<compression><type>gzip</type></compression>\n\t";
    }

    if (props.containsKey("build.compress.value"))
    {
      valSchema += "\t<compression><type>gzip</type></compression>\n\t";
    }

    if (props.containsKey("build.force.schema.key"))
    {
      keySchema = props.get("build.force.schema.key");
    }

    if (props.containsKey("build.force.schema.value"))
    {
      valSchema = props.get("build.force.schema.value");
    }

    String newStoreDefXml =
        VoldemortUtils.getStoreDefXml(storeName,
                                      replicationFactor,
                                      requiredReads,
                                      requiredWrites,
                                      props.containsKey("build.preferred.reads")
                                          ? props.getInt("build.preferred.reads") : null,
                                      props.containsKey("build.preferred.writes")
                                          ? props.getInt("build.preferred.writes") : null,
                                      (props.containsKey("push.force.schema.key"))
                                          ? props.getString("push.force.schema.key")
                                          : keySchema,
                                      (props.containsKey("push.force.schema.value"))
                                          ? props.getString("push.force.schema.value")
                                          : valSchema,
                                      description,
                                      owners);

    log.info("Verifying store: \n" + newStoreDefXml.toString());

    StoreDefinition newStoreDef = VoldemortUtils.getStoreDef(newStoreDefXml);

    // get store def from cluster
    log.info("Getting store definition from: " + url + " (node id " + this.nodeId + ")");

    AdminClient adminClient = new AdminClient(url, new AdminClientConfig());
    try
    {
      List<StoreDefinition> remoteStoreDefs =
          adminClient.getRemoteStoreDefList(this.nodeId).getValue();
      boolean foundStore = false;

      // go over all store defs and see if one has the same name as the store we're trying
      // to build
      for (StoreDefinition remoteStoreDef : remoteStoreDefs)
      {
        if (remoteStoreDef.getName().equals(storeName))
        {
          // if the store already exists, but doesn't match what we want to push, we need
          // to worry
          if (!remoteStoreDef.equals(newStoreDef))
          {
            // it is possible that the stores actually DO match, but the
            // json in the key/value serializers is out of order (eg
            // {'a': 'int32', 'b': 'int32'} could have a/b reversed.
            // this is just a reflection of the fact that voldemort json
            // type defs use hashmaps that are unordered, and pig uses
            // bags that are unordered as well. it's therefore
            // unpredictable what order the keys will come out of pig.
            // let's check to see if the key/value serializers are
            // REALLY equal.
            SerializerDefinition localKeySerializerDef = newStoreDef.getKeySerializer();
            SerializerDefinition localValueSerializerDef =
                newStoreDef.getValueSerializer();
            SerializerDefinition remoteKeySerializerDef =
                remoteStoreDef.getKeySerializer();
            SerializerDefinition remoteValueSerializerDef =
                remoteStoreDef.getValueSerializer();

            if (remoteKeySerializerDef.getName().equals("json")
                && remoteValueSerializerDef.getName().equals("json")
                && remoteKeySerializerDef.getAllSchemaInfoVersions().size() == 1
                && remoteValueSerializerDef.getAllSchemaInfoVersions().size() == 1)
            {
              JsonTypeDefinition remoteKeyDef =
                  JsonTypeDefinition.fromJson(remoteKeySerializerDef.getCurrentSchemaInfo());
              JsonTypeDefinition remoteValDef =
                  JsonTypeDefinition.fromJson(remoteValueSerializerDef.getCurrentSchemaInfo());
              JsonTypeDefinition localKeyDef =
                  JsonTypeDefinition.fromJson(localKeySerializerDef.getCurrentSchemaInfo());
              JsonTypeDefinition localValDef =
                  JsonTypeDefinition.fromJson(localValueSerializerDef.getCurrentSchemaInfo());

              if (remoteKeyDef.equals(localKeyDef) && remoteValDef.equals(localValDef))
              {
                // if the key/value serializers are REALLY equal
                // (even though the strings may not match), then
                // just use the remote stores to GUARANTEE that they
                // match, and try again.
                newStoreDefXml =
                    VoldemortUtils.getStoreDefXml(storeName,
                                                  replicationFactor,
                                                  requiredReads,
                                                  requiredWrites,
                                                  props.containsKey("build.preferred.reads")
                                                      ? props.getInt("build.preferred.reads")
                                                      : null,
                                                  props.containsKey("build.preferred.writes")
                                                      ? props.getInt("build.preferred.writes")
                                                      : null,
                                                  "\n\t\t<type>json</type>\n\t\t<schema-info version=\"0\">"
                                                      + remoteKeySerializerDef.getCurrentSchemaInfo()
                                                      + "</schema-info>\n\t",
                                                  "\n\t\t<type>json</type>\n\t\t<schema-info version=\"0\">"
                                                      + remoteValueSerializerDef.getCurrentSchemaInfo()
                                                      + "</schema-info>\n\t");

                newStoreDef = VoldemortUtils.getStoreDef(newStoreDefXml);

                if (!remoteStoreDef.equals(newStoreDef))
                {
                  // if we still get a fail, then we know that the
                  // store defs don't match for reasons OTHER than
                  // the key/value serializer
                  throw new RuntimeException("Your store schema is identical, but the store definition does not match. Have: "
                      + newStoreDef + "\nBut expected: " + remoteStoreDef);
                }
              }
              else
              {
                // if the key/value serializers are not equal (even
                // in java, not just json strings), then fail
                throw new RuntimeException("Your store definition does not match the store definition that is already in the cluster. Tried to resolve identical schemas between local and remote, but failed. Have: "
                    + newStoreDef + "\nBut expected: " + remoteStoreDef);
              }
            }
          }

          foundStore = true;
          break;
        }
      }

      // if the store doesn't exist yet, create it
      if (!foundStore)
      {
        // New requirement - Make sure the user had description and owner specified
        if (description.length() == 0)
        {
          throw new RuntimeException("Description field missing in store definition. "
              + "Please add \"push.store.description\" with a line describing your store");
        }

        if (owners.length() == 0)
        {
          throw new RuntimeException("Owner field missing in store definition. "
              + "Please add \"push.store.owners\" with value being comma-separated list of LinkedIn email ids");

        }

        log.info("Could not find store " + storeName
            + " on Voldemort. Adding it to all nodes ");
        adminClient.addStore(newStoreDef);
      }

      // don't use newStoreDef because we want to ALWAYS use the JSON
      // definition since the store builder assumes that you are using
      // JsonTypeSerializer. This allows you to tweak your value/key store xml
      // as you see fit, but still uses the json sequence file meta data to
      // build the store.
      storeDefs =
          ImmutableList.of(VoldemortUtils.getStoreDef(VoldemortUtils.getStoreDefXml(storeName,
                                                                                    replicationFactor,
                                                                                    requiredReads,
                                                                                    requiredWrites,
                                                                                    props.containsKey("build.preferred.reads")
                                                                                        ? props.getInt("build.preferred.reads")
                                                                                        : null,
                                                                                    props.containsKey("build.preferred.writes")
                                                                                        ? props.getInt("build.preferred.writes")
                                                                                        : null,
                                                                                    keySchema,
                                                                                    valSchema)));
      cluster = adminClient.getAdminClientCluster();
    }
    finally
    {
      adminClient.stop();
    }
  }

  public String runBuildStore(Props props, String url) throws Exception
  {
    int replicationFactor = props.getInt("build.replication.factor", 2);
    int chunkSize = props.getInt("build.chunk.size", 1024 * 1024 * 1024);
    Path tempDir =
        new Path(props.getString("build.temp.dir", "/tmp/vold-build-and-push-"
            + new Random().nextLong()));
    URI uri = new URI(url);
    Path outputDir = new Path(props.getString("build.output.dir"), uri.getHost());
    Path inputPath = getInputPath();
    String keySelection = props.getString("build.key.selection", null);
    String valSelection = props.getString("build.value.selection", null);
    CheckSumType checkSumType =
        CheckSum.fromString(props.getString("checksum.type",
                                            CheckSum.toString(CheckSumType.MD5)));
    boolean saveKeys = props.getBoolean("save.keys", true);
    boolean reducerPerBucket = props.getBoolean("reducer.per.bucket", false);
    int numChunks = props.getInt("num.chunks", -1);

    new VoldemortBTreeStoreBuilderJob(this.getId() + "-build-store",
                                      props,
                                      new VoldemortStoreBuilderConf(replicationFactor,
                                                                    chunkSize,
                                                                    tempDir,
                                                                    outputDir,
                                                                    inputPath,
                                                                    cluster,
                                                                    storeDefs,
                                                                    storeName,
                                                                    keySelection,
                                                                    valSelection,
                                                                    null,
                                                                    null,
                                                                    checkSumType,
                                                                    saveKeys,
                                                                    reducerPerBucket,
                                                                    numChunks)).run();
    return outputDir.toString();
  }

  public void runPushStore(Props props, String url, String dataDir) throws Exception
  {
    // For backwards compatibility http timeout = admin timeout
    int httpTimeoutMs = 1000 * props.getInt("push.http.timeout.seconds", 24 * 60 * 60);
    long pushVersion = props.getLong("push.version", -1L);
    if (props.containsKey("push.version.timestamp"))
    {
      DateFormat format = new SimpleDateFormat("yyyyMMddHHmmss");
      pushVersion = Long.parseLong(format.format(new Date()));
    }
    int maxBackoffDelayMs = 1000 * props.getInt("push.backoff.delay.seconds", 60);
    boolean rollback = props.getBoolean("push.rollback", true);

    new VoldemortSwapJob(this.getId() + "-push-store",
                         props,
                         new VoldemortSwapConf(cluster,
                                               dataDir,
                                               storeName,
                                               httpTimeoutMs,
                                               pushVersion,
                                               maxBackoffDelayMs,
                                               rollback)).run();
  }

  /**
   * Get the sanitized input path. At the moment of writing, this means the #LATEST tag is
   * expanded.
   */
  private Path getInputPath() throws IOException
  {
    Path path = new Path(props.getString("build.input.path"));
    return HadoopUtils.getSanitizedPath(path);
  }

}
