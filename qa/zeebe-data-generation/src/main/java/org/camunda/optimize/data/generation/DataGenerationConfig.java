/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.data.generation;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.ZeebeClientBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadFactory;

@Configuration
public class DataGenerationConfig {
  public static final String DATA_INSTANCE_COUNT = "DATA_INSTANCE_COUNT";
  public static final String DATA_PROCESS_DEFINITION_COUNT = "DATA_PROCESS_DEFINITION_COUNT";

  private static final String INSTANCE_STARTER_THREAD_COUNT = "INSTANCE_STARTER_THREAD_COUNT";
  private static final int JOB_WORKER_MAX_JOBS_ACTIVE = 5;
  private static final String ZEEBE_GATEWAY_ADDRESS = "localhost:26500";

  public ZeebeClient createZeebeClient() {
    final ZeebeClientBuilder builder = ZeebeClient.newClientBuilder()
      .gatewayAddress(ZEEBE_GATEWAY_ADDRESS)
      .defaultJobWorkerMaxJobsActive(JOB_WORKER_MAX_JOBS_ACTIVE)
      .usePlaintext();
    return builder.build();
  }

  @Bean
  public ZeebeClient getZeebeClient() {
    return createZeebeClient();
  }

  @SuppressWarnings("unused")
  @Bean("dataGeneratorThreadPoolExecutor")
  public ThreadPoolTaskExecutor getDataGeneratorTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setThreadFactory(getThreadFactory());
    executor.setCorePoolSize(Integer.getInteger(INSTANCE_STARTER_THREAD_COUNT));
    executor.setMaxPoolSize(Integer.getInteger(INSTANCE_STARTER_THREAD_COUNT));
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
                                                this.nextThreadName(), createZeebeClient()
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
