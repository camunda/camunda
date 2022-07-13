/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.data.generation;

import java.util.concurrent.ThreadFactory;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.ZeebeClientBuilder;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class DataGeneratorConfig {

  private static final int JOB_WORKER_MAX_JOBS_ACTIVE = 5;

  @Autowired
  private DataGeneratorProperties dataGeneratorProperties;

  public ZeebeClient createZeebeClient() {
    String gatewayAddress = dataGeneratorProperties.getZeebeGatewayAddress();
    final ZeebeClientBuilder builder = ZeebeClient.newClientBuilder()
      .gatewayAddress(gatewayAddress)
      .defaultJobWorkerMaxJobsActive(JOB_WORKER_MAX_JOBS_ACTIVE)
      .usePlaintext();
    return builder.build();
  }

  @Bean
  public ZeebeClient getZeebeClient() {
    return createZeebeClient();
  }

  @Bean
  public RestHighLevelClient createRestHighLevelClient(){
    return new RestHighLevelClient(
      RestClient.builder(new HttpHost(dataGeneratorProperties.getElasticsearchHost(), dataGeneratorProperties.getElasticsearchPort(), "http")));
  }


  @Bean("dataGeneratorThreadPoolExecutor")
  public ThreadPoolTaskExecutor getDataGeneratorTaskExecutor(DataGeneratorProperties dataGeneratorProperties) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setThreadFactory(getThreadFactory());
    executor.setCorePoolSize(dataGeneratorProperties.getThreadCount());
    executor.setMaxPoolSize(dataGeneratorProperties.getThreadCount());
    executor.setQueueCapacity(1);
    executor.initialize();
    return executor;
  }

  @Bean
  public ThreadFactory getThreadFactory() {
    return new CustomizableThreadFactory("data_generator_") {
      @Override
      public Thread newThread(final Runnable runnable) {
        Thread thread = new DataGeneratorThread(this.getThreadGroup(), runnable,
            this.nextThreadName(), createZeebeClient());
        thread.setPriority(this.getThreadPriority());
        thread.setDaemon(this.isDaemon());
        return thread;
      }
    };
  }

  public class DataGeneratorThread extends Thread {

    private ZeebeClient zeebeClient;

    public DataGeneratorThread(final ZeebeClient zeebeClient) {
      this.zeebeClient = zeebeClient;
    }

    public DataGeneratorThread(final Runnable target, final ZeebeClient zeebeClient) {
      super(target);
      this.zeebeClient = zeebeClient;
    }

    public DataGeneratorThread(final ThreadGroup group,
        final Runnable target, final ZeebeClient zeebeClient) {
      super(group, target);
      this.zeebeClient = zeebeClient;
    }

    public DataGeneratorThread(final String name,
        final ZeebeClient zeebeClient) {
      super(name);
      this.zeebeClient = zeebeClient;
    }

    public DataGeneratorThread(final ThreadGroup group,
        final String name, final ZeebeClient zeebeClient) {
      super(group, name);
      this.zeebeClient = zeebeClient;
    }

    public DataGeneratorThread(final Runnable target, final String name,
        final ZeebeClient zeebeClient) {
      super(target, name);
      this.zeebeClient = zeebeClient;
    }

    public DataGeneratorThread(final ThreadGroup group,
        final Runnable target, final String name,
        final ZeebeClient zeebeClient) {
      super(group, target, name);
      this.zeebeClient = zeebeClient;
    }

    public DataGeneratorThread(final ThreadGroup group,
        final Runnable target, final String name, final long stackSize,
        final ZeebeClient zeebeClient) {
      super(group, target, name, stackSize);
      this.zeebeClient = zeebeClient;
    }

    public DataGeneratorThread(final ThreadGroup group, final Runnable target, final String name,
        final long stackSize,
        final boolean inheritThreadLocals, final ZeebeClient zeebeClient) {
      super(group, target, name, stackSize, inheritThreadLocals);
      this.zeebeClient = zeebeClient;
    }

    public ZeebeClient getZeebeClient() {
      return zeebeClient;
    }
  }

}
