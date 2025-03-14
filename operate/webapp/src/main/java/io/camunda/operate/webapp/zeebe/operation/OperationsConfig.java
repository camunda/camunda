/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.zeebe.operation;

import io.camunda.config.operate.OperateProperties;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class OperationsConfig {

  @Autowired private OperateProperties operateProperties;

  @Bean("operationsThreadPoolExecutor")
  public ThreadPoolTaskExecutor getOperationsThreadPoolExecutor() {
    final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(operateProperties.getOperationExecutor().getThreadsCount());
    executor.setMaxPoolSize(operateProperties.getOperationExecutor().getThreadsCount());
    executor.setQueueCapacity(operateProperties.getOperationExecutor().getQueueSize());
    executor.setRejectedExecutionHandler(new BlockCallerUntilExecutorHasCapacity());
    executor.setThreadNamePrefix("operation_executor_");
    executor.initialize();
    return executor;
  }

  private final class BlockCallerUntilExecutorHasCapacity implements RejectedExecutionHandler {
    public void rejectedExecution(Runnable runnable, ThreadPoolExecutor executor) {
      // this will block if the queue is full
      if (!executor.isShutdown()) {
        try {
          executor.getQueue().put(runnable);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
    }
  }
}
