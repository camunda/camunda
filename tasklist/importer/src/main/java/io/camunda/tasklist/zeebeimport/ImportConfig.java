/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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

  @Bean("importThreadPoolExecutor")
  public ThreadPoolTaskExecutor getTaskExecutor() {
    final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(tasklistProperties.getImporter().getThreadsCount());
    executor.setMaxPoolSize(tasklistProperties.getImporter().getThreadsCount());
    executor.setThreadNamePrefix("import_");
    executor.initialize();
    return executor;
  }

  @Bean("recordsReaderThreadPoolExecutor")
  public ThreadPoolTaskScheduler getRecordsReaderTaskExecutor() {
    final var executor = new ThreadPoolTaskScheduler();
    executor.setPoolSize(tasklistProperties.getImporter().getReaderThreadsCount());
    executor.setThreadNamePrefix("records_reader_");
    executor.initialize();
    return executor;
  }

  @Bean("importPositionUpdateThreadPoolExecutor")
  public ThreadPoolTaskScheduler getImportPositionUpdateTaskExecutor() {
    final var executor = new ThreadPoolTaskScheduler();
    executor.setPoolSize(1);
    executor.setThreadNamePrefix("import_position_update_");
    executor.initialize();
    return executor;
  }
}
