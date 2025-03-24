/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rest;

import io.camunda.application.commons.rest.RestApiConfiguration.GatewayRestProperties;
import io.camunda.service.ProcessDefinitionServices;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.gateway.rest.ConditionalOnRestGatewayEnabled;
import io.camunda.zeebe.gateway.rest.cache.ProcessCache;
import io.camunda.zeebe.gateway.rest.config.GatewayRestConfiguration;
import io.camunda.zeebe.gateway.rest.util.ProcessFlowNodeProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackages = "io.camunda.zeebe.gateway.rest")
@ConditionalOnRestGatewayEnabled
@EnableConfigurationProperties(GatewayRestProperties.class)
public class RestApiConfiguration {

  @Bean
  public ProcessFlowNodeProvider processFlowNodeProvider(
      final ProcessDefinitionServices processDefinitionServices) {
    return new ProcessFlowNodeProvider(processDefinitionServices);
  }

  @Bean
  public ProcessCache processCache(
      final GatewayRestConfiguration configuration,
      final ProcessFlowNodeProvider processFlowNodeProvider,
      final BrokerTopologyManager brokerTopologyManager) {

    return new ProcessCache(configuration, processFlowNodeProvider, brokerTopologyManager);
  }

  @ConfigurationProperties("camunda.rest")
  public static final class GatewayRestProperties extends GatewayRestConfiguration {}
}
