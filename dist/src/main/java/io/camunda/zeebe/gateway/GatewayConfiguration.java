/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway;

import io.camunda.zeebe.gateway.GatewayConfiguration.GatewayProperties;
import io.camunda.zeebe.gateway.impl.configuration.GatewayCfg;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GatewayProperties.class)
public final class GatewayConfiguration {

  private final GatewayProperties config;

  @Autowired
  public GatewayConfiguration(final GatewayProperties config) {
    this.config = config;
    config.init();
  }

  @Bean
  public GatewayProperties config() {
    return config;
  }

  @ConfigurationProperties("zeebe.gateway")
  public static final class GatewayProperties extends GatewayCfg {}
}
