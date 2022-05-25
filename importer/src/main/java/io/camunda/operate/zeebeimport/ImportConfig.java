/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.zeebeimport;

import io.camunda.operate.property.OperateProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class ImportConfig {

  @Autowired
  private OperateProperties operateProperties;

  @Bean("importThreadPoolExecutor")
  public ThreadPoolTaskExecutor getTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(operateProperties.getImporter().getThreadsCount());
    executor.setMaxPoolSize(operateProperties.getImporter().getThreadsCount());
    executor.setThreadNamePrefix("import_");
    executor.initialize();
    return executor;
  }

  @Bean("postImportThreadPoolScheduler")
  public ThreadPoolTaskScheduler getPostImportTaskScheduler(OperateProperties operateProperties) {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(operateProperties.getImporter().getPostImportThreadsCount());
    scheduler.setThreadNamePrefix("postimport_");
    scheduler.initialize();
    return scheduler;
  }

  @Bean("recordsReaderThreadPoolExecutor")
  public ThreadPoolTaskScheduler getRecordsReaderTaskExecutor() {
    ThreadPoolTaskScheduler executor = new ThreadPoolTaskScheduler();
    executor.setPoolSize(operateProperties.getImporter().getReaderThreadsCount());
    executor.setThreadNamePrefix("records_reader_");
    executor.initialize();
    return executor;
  }
}
