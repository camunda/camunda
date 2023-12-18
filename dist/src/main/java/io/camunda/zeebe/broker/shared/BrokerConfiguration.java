/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.shared;

import io.camunda.zeebe.broker.shared.BrokerConfiguration.BrokerProperties;
import io.camunda.zeebe.broker.shared.WorkingDirectoryConfiguration.WorkingDirectory;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BrokerProperties.class)
public final class BrokerConfiguration {

  private final WorkingDirectory workingDirectory;
  private final BrokerCfg properties;

  @Autowired
  public BrokerConfiguration(
      final WorkingDirectory workingDirectory, final BrokerProperties properties) {
    this.workingDirectory = workingDirectory;
    this.properties = properties;

    properties.init(workingDirectory.path().toAbsolutePath().toString());
  }

  @Bean
  public BrokerCfg config() {
    return properties;
  }

  public WorkingDirectory workingDirectory() {
    return workingDirectory;
  }

  @ConfigurationProperties("zeebe.broker")
  public static final class BrokerProperties extends BrokerCfg {}
}
