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
import io.camunda.zeebe.gateway.impl.configuration.ExperimentalCfg;
import io.camunda.zeebe.gateway.impl.configuration.MultiTenancyCfg;
import io.camunda.zeebe.gateway.rest.ConditionalOnRestGatewayEnabled.RestGatewayDisabled;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.context.LifecycleProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BrokerProperties.class)
public final class BrokerConfiguration {

  private final WorkingDirectory workingDirectory;
  private final BrokerCfg properties;
  private final LifecycleProperties lifecycle;

  @Autowired
  public BrokerConfiguration(
      final WorkingDirectory workingDirectory,
      final BrokerProperties properties,
      final LifecycleProperties lifecycle) {
    this.workingDirectory = workingDirectory;
    this.properties = properties;
    this.lifecycle = lifecycle;

    properties.init(workingDirectory.path().toAbsolutePath().toString());
  }

  public BrokerCfg config() {
    return properties;
  }

  public WorkingDirectory workingDirectory() {
    return workingDirectory;
  }

  @ConditionalOnProperty(prefix = "zeebe.broker.gateway", name = "enable", havingValue = "false")
  @Bean
  public RestGatewayDisabled disableRestGateway() {
    return new RestGatewayDisabled();
  }

  public Duration shutdownTimeout() {
    return lifecycle.getTimeoutPerShutdownPhase();
  }

  @Bean
  public MultiTenancyCfg multiTenancyCfg() {
    return properties.getGateway().getMultiTenancy();
  }

  @Bean
  public ExperimentalCfg experimentalCfg() {
    return properties.getGateway().getExperimental();
  }

  @ConfigurationProperties("zeebe.broker")
  public static final class BrokerProperties extends BrokerCfg {}
}
