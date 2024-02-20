/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate;

import io.camunda.operate.property.OperateProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@ComponentScan(
    basePackages = "io.camunda.operate.archiver",
    nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
@ConditionalOnProperty(
    name = "camunda.operate.archiverEnabled",
    havingValue = "true",
    matchIfMissing = true)
public class ArchiverModuleConfiguration {

  private static final Logger logger = LoggerFactory.getLogger(ArchiverModuleConfiguration.class);

  @PostConstruct
  public void logModule() {
    logger.info("Starting module: archiver");
  }

  @Autowired private OperateProperties operateProperties;

  @Bean("archiverThreadPoolExecutor")
  public ThreadPoolTaskScheduler getTaskScheduler() {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(operateProperties.getArchiver().getThreadsCount());
    scheduler.setThreadNamePrefix("archiver_");
    scheduler.initialize();
    return scheduler;
  }
}
