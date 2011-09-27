package com.linkedin.batch.jobs;

import java.io.IOException;
import java.util.Random;
import java.util.Map.Entry;

import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.FileInputFormat;
import org.apache.hadoop.mapred.FileOutputFormat;
import org.apache.hadoop.mapred.JobClient;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.Mapper;
import org.apache.hadoop.mapred.OutputCollector;
import org.apache.hadoop.mapred.Reporter;
import org.apache.hadoop.mapred.TextInputFormat;
import org.apache.hadoop.mapred.TextOutputFormat;
import org.apache.hadoop.mapred.lib.IdentityReducer;
import org.apache.log4j.Logger;

import azkaban.common.utils.Props;

import com.linkedin.batch.serialization.JsonSequenceFileInputFormat;
import com.linkedin.batch.utils.HadoopUtils;

public class DumbMySQLJob extends AbstractHadoopJob
{

  private final static Logger logger = Logger.getLogger(DumbMySQLJob.class);

  public DumbMySQLJob(String name, Props props)
  {
    super(name, props);
  }

  @Override
  public void run() throws Exception
  {
    for (int i = 5; i < 6; i++)
    {
      getProps().put("input.paths", "/user/rsumbaly/input");
      getProps().put("output.path", "/tmp/mysql-output-" + Integer.toString(i));
      getProps().put("num.keys", (long) Math.pow(2, i) * 1048576);
      JobConf conf = createJobConf(DumbMapper.class, IdentityReducer.class);
      conf.setSpeculativeExecution(false);
      conf.setInputFormat(JsonSequenceFileInputFormat.class);
      conf.setOutputFormat(TextOutputFormat.class);
      conf.setOutputKeyClass(BytesWritable.class);
      conf.setOutputValueClass(BytesWritable.class);
      conf.setJarByClass(getClass());
      conf.setNumReduceTasks(1);
      logger.info("output added:" + getProps().getString("output.path"));
      logger.info("Running dumb-mysql-data.");
      JobClient.runJob(conf);
    }
  }

  public static final class DumbMapper implements
      Mapper<BytesWritable, BytesWritable, BytesWritable, BytesWritable>
  {

    private long   numKeys;
    private long   partition;
    private long   startKey;
    private long   slab;
    private Random random;

    @Override
    public void configure(JobConf conf)
    {
      this.partition = conf.getInt("mapred.task.partition", -1);
      int numMappers = conf.getNumMapTasks();
      if (this.partition == -1)
      {
        throw new RuntimeException("Could not retrieve task partition");
      }
      Props props = HadoopUtils.getPropsFromJob(conf);
      if (!props.containsKey("num.keys"))
      {
        throw new RuntimeException("Could not get num keys");
      }
      this.numKeys = props.getLong("num.keys");
      logger.info("Number of keys - " + numKeys);
      this.slab = numKeys / numMappers;
      this.startKey = partition * slab;
      logger.info("Partition number " + partition + " - Responsible for " + startKey
          + "  slab " + slab);
      this.random = new Random();

    }

    public void map(BytesWritable key,
                    BytesWritable value,
                    OutputCollector<BytesWritable, BytesWritable> output,
                    Reporter reporter) throws IOException
    {
      for (int index = 0; index < slab; index++)
      {
        byte[] randomBytes = new byte[1024];
        random.nextBytes(randomBytes);
        output.collect(new BytesWritable(Long.toString(startKey + index).getBytes()),
                       new BytesWritable(randomBytes));
      }
    }

    @Override
    public void close() throws IOException
    {
      // TODO Auto-generated method stub

    }
  }
}
