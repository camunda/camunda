/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport;

import io.camunda.config.operate.OperateProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class ImportConfig {

  @Autowired private OperateProperties operateProperties;

  @Bean("importThreadPoolExecutor")
  public ThreadPoolTaskExecutor getTaskExecutor() {
    final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(operateProperties.getImporter().getThreadsCount());
    executor.setMaxPoolSize(operateProperties.getImporter().getThreadsCount());
    executor.setThreadNamePrefix("import_");
    executor.initialize();
    return executor;
  }

  @Bean("recordsReaderThreadPoolExecutor")
  public ThreadPoolTaskScheduler getRecordsReaderTaskExecutor() {
    final ThreadPoolTaskScheduler executor = new ThreadPoolTaskScheduler();
    executor.setPoolSize(operateProperties.getImporter().getReaderThreadsCount());
    executor.setThreadNamePrefix("records_reader_");
    executor.initialize();
    return executor;
  }

  @Bean("importPositionUpdateThreadPoolExecutor")
  public ThreadPoolTaskScheduler getImportPositionUpdateTaskExecutor() {
    final ThreadPoolTaskScheduler executor = new ThreadPoolTaskScheduler();
    executor.setPoolSize(1);
    executor.setThreadNamePrefix("import_position_update_");
    executor.initialize();
    return executor;
  }
}
