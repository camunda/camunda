/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.configuration;

import io.camunda.application.commons.configuration.WorkingDirectoryConfiguration.WorkingDirectory;
import io.camunda.configuration.beans.RestoreBasedProperties;
import io.camunda.zeebe.broker.system.configuration.RestoreCfg;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RestoreBasedProperties.class)
@Profile(value = {"restore"})
public class RestoreBasedConfiguration {

  private final WorkingDirectory workingDirectory;
  private final RestoreCfg properties;

  @Autowired
  public RestoreBasedConfiguration(
      final WorkingDirectory workingDirectory,
      final RestoreBasedProperties properties) {
    this.workingDirectory = workingDirectory;
    this.properties = properties;

    properties.init(null, workingDirectory.path().toAbsolutePath().toString());
  }

  public RestoreCfg config() {
    return properties;
  }

  public WorkingDirectory workingDirectory() {
    return workingDirectory;
  }
}