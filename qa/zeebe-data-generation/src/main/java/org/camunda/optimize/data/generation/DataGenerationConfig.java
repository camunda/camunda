/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.data.generation;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.ZeebeClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadFactory;

@Configuration
public class DataGenerationConfig {
  private static final String ZEEBE_GATEWAY_ADDRESS = "localhost:26500";
  @Value("${INSTANCE_STARTER_THREAD_COUNT:8}")
  private Integer threadCount;
  @Value("${JOBWORKER_MAX_ACTIVE_COUNT:5}")
  private Integer jobworkerCount;
  @Value("${JOBWORKER_EXECUTION_THREAD_COUNT:1}")
  private Integer jobworkerExecutionThreadCount;

  @Bean
  public ZeebeClient getZeebeClient() {
    final ZeebeClientBuilder builder = ZeebeClient.newClientBuilder()
      .gatewayAddress(ZEEBE_GATEWAY_ADDRESS)
      .defaultJobWorkerMaxJobsActive(jobworkerCount)
      .numJobWorkerExecutionThreads(jobworkerExecutionThreadCount)
      .usePlaintext();
    return builder.build();
  }

  @SuppressWarnings("unused")
  @Bean("dataGeneratorThreadPoolExecutor")
  public ThreadPoolTaskExecutor getDataGeneratorTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setThreadFactory(getThreadFactory());
    executor.setCorePoolSize(threadCount);
    executor.setMaxPoolSize(threadCount);
    executor.setQueueCapacity(10000);
    executor.initialize();
    return executor;
  }

  @Bean
  public ThreadFactory getThreadFactory() {
    return new CustomizableThreadFactory("zeebe_data_generator_") {
      @Override
      public Thread newThread(final Runnable runnable) {
        Thread thread = new DataGeneratorThread(this.getThreadGroup(), runnable,
                                                this.nextThreadName(), getZeebeClient()
        );
        thread.setPriority(this.getThreadPriority());
        thread.setDaemon(this.isDaemon());
        return thread;
      }
    };
  }

  public class DataGeneratorThread extends Thread {

    private final ZeebeClient zeebeClient;

    public DataGeneratorThread(final ThreadGroup group,
                               final Runnable target, final String name,
                               final ZeebeClient zeebeClient) {
      super(group, target, name);
      this.zeebeClient = zeebeClient;
    }

    public ZeebeClient getZeebeClient() {
      return zeebeClient;
    }
  }
}
