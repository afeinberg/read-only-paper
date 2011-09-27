package com.linkedin.batch.jobs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.hadoop.mapred.JobClient;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.OutputCollector;
import org.apache.hadoop.mapred.Reporter;
import org.apache.log4j.Logger;

import azkaban.common.utils.Props;

import com.linkedin.batch.serialization.AbstractJsonHadoopJob;
import com.linkedin.batch.serialization.JsonMapper;
import com.linkedin.batch.utils.HadoopUtils;

public class DumbDataJob extends AbstractJsonHadoopJob
{

  private final static Logger _log = Logger.getLogger(DumbDataJob.class);

  public DumbDataJob(String name, Props props)
  {
    super(name, props);
  }

  @Override
  public void run() throws Exception
  {
    for (int i = 6; i < 7; i++)
    {
      getProps().put("input.paths", "/user/rsumbaly/input");
      getProps().put("output.path", "/tmp/output-" + Integer.toString(i));
      getProps().put("mapper.output.key.schema", "'int64'");
      getProps().put("mapper.output.value.schema", "'string'");
      getProps().put("num.keys", (long) Math.pow(2, i) * 1048576);
      JobConf jobConf = createJobConf(DumbMapper.class);
      jobConf.setSpeculativeExecution(false);
      JobClient.runJob(jobConf);
    }
  }

  public static final class DumbMapper extends JsonMapper
  {

    private long    numKeys;
    private long    partition;
    private long    startKey;
    private long    slab;
    private Random random;

    @Override
    public void configure(JobConf conf)
    {
      super.configure(conf);
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
      _log.info("Number of keys - " + numKeys);
      this.slab = numKeys / numMappers;
      this.startKey = partition * slab;
      _log.info("Partition number " + partition + " - Responsible for " + startKey
          + "  slab " + slab);
      this.random = new Random();
    }

    @Override
    public void mapObjects(Object key,
                           Object value,
                           OutputCollector<Object, Object> output,
                           Reporter reporter) throws IOException
    {
      for (int index = 0; index < slab; index++)
      {
        byte[] randomBytes = new byte[1024];
        random.nextBytes(randomBytes);
        output.collect(new Long(startKey + index), new String(randomBytes));
      }
    }
  }
}
