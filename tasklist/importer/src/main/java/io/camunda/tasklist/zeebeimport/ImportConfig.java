/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.zeebeimport;

import io.camunda.tasklist.property.TasklistProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class ImportConfig {

  @Autowired private TasklistProperties tasklistProperties;

  @Bean("tasklistImportThreadPoolExecutor")
  public ThreadPoolTaskExecutor getTaskExecutor() {
    final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(tasklistProperties.getImporter().getThreadsCount());
    executor.setMaxPoolSize(tasklistProperties.getImporter().getThreadsCount());
    executor.setThreadNamePrefix("import_");
    executor.initialize();
    return executor;
  }

  @Bean("tasklistRecordsReaderThreadPoolExecutor")
  public ThreadPoolTaskScheduler getRecordsReaderTaskExecutor() {
    final var executor = new ThreadPoolTaskScheduler();
    executor.setPoolSize(tasklistProperties.getImporter().getReaderThreadsCount());
    executor.setThreadNamePrefix("records_reader_");
    executor.initialize();
    return executor;
  }

  @Bean("tasklistImportPositionUpdateThreadPoolExecutor")
  public ThreadPoolTaskScheduler getImportPositionUpdateTaskExecutor() {
    final var executor = new ThreadPoolTaskScheduler();
    executor.setPoolSize(1);
    executor.setThreadNamePrefix("import_position_update_");
    executor.initialize();
    return executor;
  }
}
