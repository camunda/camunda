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
 * Entry point for the unified BFF webapp module. Active only when the {@code tmp-webapp} Spring
 * profile is enabled (i.e., dormant in all default deployments). The {@code tmp-webapp} profile is
 * temporary; it will be removed at the end of the <a
 * href="https://github.com/camunda/product-hub/issues/3456">epic</a> when the webapp module becomes
 * the default BFF for the legacy apps (Tasklist/Operate/Admin).
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan(
    basePackages = "io.camunda.webapp",
    nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
@Profile("tmp-webapp")
public class WebappModuleConfiguration {

  private static final Logger LOGGER = LoggerFactory.getLogger(WebappModuleConfiguration.class);

  @PostConstruct
  public void logModule() {
    LOGGER.info("Starting module: webapp");
  }
}
