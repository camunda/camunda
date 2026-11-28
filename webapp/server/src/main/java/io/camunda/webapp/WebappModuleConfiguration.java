/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapp;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;
import org.springframework.context.annotation.Profile;

/**
 * Entry point for the unified BFF webapp module. It is gated on the legacy {@code tasklist}, {@code
 * operate} and {@code admin} profiles because the unified webapp serves the Tasklist, Operate and
 * Admin UIs.
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan(
    basePackages = "io.camunda.webapp",
    nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
@Profile({"tasklist", "operate", "admin"})
public class WebappModuleConfiguration {

  private static final Logger LOGGER = LoggerFactory.getLogger(WebappModuleConfiguration.class);

  @PostConstruct
  public void logModule() {
    LOGGER.info("Starting module: webapp");
  }
}
