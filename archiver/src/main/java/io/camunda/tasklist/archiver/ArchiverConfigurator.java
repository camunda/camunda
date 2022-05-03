/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.archiver;

import io.camunda.tasklist.property.TasklistProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class ArchiverConfigurator {

  @Autowired private TasklistProperties tasklistProperties;

  @Bean("archiverThreadPoolExecutor")
  public ThreadPoolTaskScheduler getTaskScheduler() {
    final ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(tasklistProperties.getArchiver().getThreadsCount());
    scheduler.setThreadNamePrefix("archiver_");
    scheduler.initialize();
    return scheduler;
  }
}
