/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.standalone;

import io.camunda.zeebe.broker.Loggers;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;
import org.springframework.context.annotation.Profile;

@Profile("operate")
@Configuration
@ComponentScan(
    basePackages = "io.camunda.operate",
    nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
public class OperateModuleConfiguration {

  private static final Logger LOGGER = Loggers.SYSTEM_LOGGER;

  @PostConstruct
  public void logModule() {
    LOGGER.info("Starting Operate");
  }
}
