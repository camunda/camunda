/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.zeebe.operation;

import io.camunda.operate.property.OperateProperties;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class OperationsConfig {

  @Autowired
  private OperateProperties operateProperties;

  @Bean("operationsThreadPoolExecutor")
  public ThreadPoolTaskExecutor getOperationsThreadPoolExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(operateProperties.getOperationExecutor().getThreadsCount());
    executor.setMaxPoolSize(operateProperties.getOperationExecutor().getThreadsCount());
    executor.setQueueCapacity(operateProperties.getOperationExecutor().getQueueSize());
    executor.setRejectedExecutionHandler(new BlockCallerUntilExecutorHasCapacity());
    executor.setThreadNamePrefix("operation_executor_");
    executor.initialize();
    return executor;
  }

  private class BlockCallerUntilExecutorHasCapacity implements RejectedExecutionHandler {
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
