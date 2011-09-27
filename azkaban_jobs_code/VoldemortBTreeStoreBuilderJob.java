package com.linkedin.batch.jobs;

import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.mapred.InputFormat;
import org.apache.hadoop.mapred.JobConf;

import voldemort.cluster.Cluster;
import voldemort.serialization.json.JsonTypeSerializer;
import voldemort.store.StoreDefinition;
import voldemort.store.btree.mr.AbstractHadoopBTreeStoreBuilderMapper;
import voldemort.store.btree.mr.HadoopBTreeStoreBuilder;
import voldemort.store.readonly.checksum.CheckSum;
import voldemort.store.readonly.checksum.CheckSum.CheckSumType;
import voldemort.store.readonly.mr.AbstractHadoopStoreBuilderMapper;
import voldemort.store.readonly.mr.HadoopStoreBuilder;
import voldemort.xml.ClusterMapper;
import voldemort.xml.StoreDefinitionsMapper;
import azkaban.common.utils.Props;
import azkaban.common.utils.Utils;

import com.linkedin.batch.serialization.JsonSequenceFileInputFormat;
import com.linkedin.batch.utils.HadoopUtils;

/**
 * Build a voldemort store from input data.
 * 
 * @author jkreps
 * 
 */
public class VoldemortBTreeStoreBuilderJob extends AbstractHadoopJob
{
  private VoldemortStoreBuilderConf conf;

  public VoldemortBTreeStoreBuilderJob(String name, Props props) throws Exception
  {
    super(name, props);
    this.conf =
        new VoldemortStoreBuilderConf(createJobConf(VoldemortStoreBuilderMapper.class),
                                      props);
  }

  public VoldemortBTreeStoreBuilderJob(String name,
                                       Props props,
                                       VoldemortStoreBuilderConf conf) throws FileNotFoundException
  {
    super(name, props);
    this.conf = conf;
  }

  public static final class VoldemortStoreBuilderConf
  {
    private int                   replicationFactor;
    private int                   chunkSize;
    private Path                  tempDir;
    private Path                  outputDir;
    private Path                  inputPath;
    private Cluster               cluster;
    private List<StoreDefinition> storeDefs;
    private String                storeName;
    private String                keySelection;
    private String                valSelection;
    private String                keyTrans;
    private String                valTrans;
    private CheckSumType          checkSumType;
    private boolean               saveKeys;
    private boolean               reducerPerBucket;
    private int                   numChunks = -1;

    public VoldemortStoreBuilderConf(int replicationFactor,
                                     int chunkSize,
                                     Path tempDir,
                                     Path outputDir,
                                     Path inputPath,
                                     Cluster cluster,
                                     List<StoreDefinition> storeDefs,
                                     String storeName,
                                     String keySelection,
                                     String valSelection,
                                     String keyTrans,
                                     String valTrans,
                                     CheckSumType checkSumType,
                                     boolean saveKeys,
                                     boolean reducerPerBucket,
                                     int numChunks)
    {
      this.replicationFactor = replicationFactor;
      this.chunkSize = chunkSize;
      this.tempDir = tempDir;
      this.outputDir = outputDir;
      this.inputPath = inputPath;
      this.cluster = cluster;
      this.storeDefs = storeDefs;
      this.storeName = storeName;
      this.keySelection = keySelection;
      this.valSelection = valSelection;
      this.keyTrans = keyTrans;
      this.valTrans = valTrans;
      this.checkSumType = checkSumType;
      this.saveKeys = saveKeys;
      this.reducerPerBucket = reducerPerBucket;
      this.numChunks = numChunks;
    }

    // requires job conf in order to get files from the filesystem
    public VoldemortStoreBuilderConf(JobConf configuration, Props props) throws Exception
    {
      this(props.getInt("replication.factor", 2),
           props.getInt("chunk.size", 1024 * 1024 * 1024),
           new Path(props.getString("temp.dir", "/tmp/vold-build-and-push-"
               + new Random().nextLong())),
           new Path(props.getString("output.dir")),
           new Path(props.getString("input.path")),
           new ClusterMapper().readCluster(new InputStreamReader(new Path(props.getString("cluster.xml")).getFileSystem(configuration)
                                                                                                         .open(new Path(props.getString("cluster.xml"))))),
           new StoreDefinitionsMapper().readStoreList(new InputStreamReader(new Path(props.getString("stores.xml")).getFileSystem(configuration)
                                                                                                                   .open(new Path(props.getString("stores.xml"))))),
           props.getString("store.name"),
           props.getString("key.selection", null),
           props.getString("value.selection", null),
           props.getString("key.transformation.class", null),
           props.getString("value.transformation.class", null),
           CheckSum.fromString(props.getString("checksum.type",
                                               CheckSum.toString(CheckSumType.MD5))),
           props.getBoolean("save.keys", true),
           props.getBoolean("reducer.per.bucket", false),
           props.getInt("num.chunks", 1));
    }

    public int getReplicationFactor()
    {
      return replicationFactor;
    }

    public int getChunkSize()
    {
      return chunkSize;
    }

    public Path getTempDir()
    {
      return tempDir;
    }

    public Path getOutputDir()
    {
      return outputDir;
    }

    public Path getInputPath()
    {
      return inputPath;
    }

    public String getStoreName()
    {
      return storeName;
    }

    public String getKeySelection()
    {
      return keySelection;
    }

    public String getValSelection()
    {
      return valSelection;
    }

    public String getKeyTrans()
    {
      return keyTrans;
    }

    public String getValTrans()
    {
      return valTrans;
    }

    public Cluster getCluster()
    {
      return cluster;
    }

    public List<StoreDefinition> getStoreDefs()
    {
      return storeDefs;
    }

    public CheckSumType getCheckSumType()
    {
      return checkSumType;
    }

    public boolean getSaveKeys()
    {
      return saveKeys;
    }

    public boolean getReducerPerBucket()
    {
      return reducerPerBucket;
    }

    public int getNumChunks()
    {
      return numChunks;
    }
  }

  @Override
  public void run() throws Exception
  {
    JobConf configuration = this.createJobConf(VoldemortStoreBuilderMapper.class);
    int chunkSize = conf.getChunkSize();
    Path tempDir = conf.getTempDir();
    Path outputDir = conf.getOutputDir();
    Path inputPath = conf.getInputPath();
    Cluster cluster = conf.getCluster();
    List<StoreDefinition> storeDefs = conf.getStoreDefs();
    String storeName = conf.getStoreName();
    CheckSumType checkSumType = conf.getCheckSumType();
    boolean saveKeys = conf.getSaveKeys();
    boolean reducerPerBucket = conf.getReducerPerBucket();

    StoreDefinition storeDef = null;
    for (StoreDefinition def : storeDefs)
      if (storeName.equals(def.getName()))
        storeDef = def;
    if (storeDef == null)
      throw new IllegalArgumentException("Store '" + storeName + "' not found.");

    FileSystem fs = outputDir.getFileSystem(configuration);
    if (fs.exists(outputDir))
    {
      info("Deleting previous output in " + outputDir + " for building store "
          + storeName);
      fs.delete(outputDir, true);
    }

    HadoopBTreeStoreBuilder builder =
        new HadoopBTreeStoreBuilder(configuration,
                                    VoldemortStoreBuilderMapper.class,
                                    JsonSequenceFileInputFormat.class,
                                    cluster,
                                    storeDef,
                                    tempDir,
                                    outputDir,
                                    inputPath,
                                    conf.getNumChunks());
    builder.build();
  }

  public static class VoldemortStoreBuilderMapper extends
      AbstractHadoopBTreeStoreBuilderMapper<Object, Object>
  {

    private String                     _keySelection;
    private String                     _valSelection;
    private JsonTypeSerializer         _inputKeySerializer;
    private JsonTypeSerializer         _inputValueSerializer;
    private StoreBuilderTransformation _keyTrans;
    private StoreBuilderTransformation _valTrans;

    @Override
    public Object makeKey(Object key, Object value)
    {
      return makeResult((BytesWritable) key,
                        _inputKeySerializer,
                        _keySelection,
                        _keyTrans);
    }

    @Override
    public Object makeValue(Object key, Object value)
    {
      return makeResult((BytesWritable) value,
                        _inputValueSerializer,
                        _valSelection,
                        _valTrans);
    }

    private Object makeResult(BytesWritable writable,
                              JsonTypeSerializer serializer,
                              String selection,
                              StoreBuilderTransformation trans)
    {
      Object obj = serializer.toObject(writable.get());
      if (selection != null)
      {
        Map m = (Map) obj;
        obj = m.get(selection);
      }

      if (trans != null)
        obj = trans.transform(obj);

      return obj;
    }

    @Override
    public void configure(JobConf conf)
    {
      super.configure(conf);
      Props props = HadoopUtils.getPropsFromJob(conf);

      _keySelection = props.getString("key.selection", null);
      _valSelection = props.getString("value.selection", null);
      _inputKeySerializer = getSchemaFromJob(conf, "mapper.input.key.schema");
      _inputValueSerializer = getSchemaFromJob(conf, "mapper.input.value.schema");
      String _keyTransClass = props.getString("key.transformation.class", null);
      String _valueTransClass = props.getString("value.transformation.class", null);

      if (_keyTransClass != null)
        _keyTrans = (StoreBuilderTransformation) Utils.callConstructor(_keyTransClass);
      if (_valueTransClass != null)
        _valTrans = (StoreBuilderTransformation) Utils.callConstructor(_valueTransClass);
    }

    protected JsonTypeSerializer getSchemaFromJob(JobConf conf, String key)
    {
      if (conf.get(key) == null)
        throw new IllegalArgumentException("Missing required parameter '" + key
            + "' on job.");
      return new JsonTypeSerializer(conf.get(key));
    }

  }

}
