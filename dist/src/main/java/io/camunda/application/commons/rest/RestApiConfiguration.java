/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rest;

import io.camunda.application.commons.rest.RestApiConfiguration.GatewayRestProperties;
import io.camunda.authentication.DefaultCamundaAuthenticationConverter;
import io.camunda.authentication.DefaultCamundaAuthenticationProvider;
import io.camunda.security.auth.CamundaAuthenticationConverter;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.ProcessDefinitionServices;
import io.camunda.service.cache.ProcessCache;
import io.camunda.service.cache.ProcessElementProvider;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.gateway.rest.ConditionalOnRestGatewayEnabled;
import io.camunda.zeebe.gateway.rest.config.GatewayRestConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackages = "io.camunda.zeebe.gateway.rest")
@ConditionalOnRestGatewayEnabled
@EnableConfigurationProperties(GatewayRestProperties.class)
public class RestApiConfiguration {

  @Bean
  public ProcessElementProvider processElementProvider(
      final ProcessDefinitionServices processDefinitionServices) {
    return new ProcessElementProvider(processDefinitionServices);
  }

  @Bean
  public ProcessCache processCache(
      final GatewayRestConfiguration configuration,
      final ProcessElementProvider processElementProvider,
      final BrokerTopologyManager brokerTopologyManager,
      final MeterRegistry meterRegistry) {

    final var cacheConfiguration =
        new ProcessCache.Configuration(
            configuration.getProcessCache().getMaxSize(),
            configuration.getProcessCache().getExpirationIdleMillis());

    return new ProcessCache(
        cacheConfiguration, processElementProvider, brokerTopologyManager, meterRegistry);
  }

  @Bean
  public CamundaAuthenticationConverter<Authentication> camundaAuthenticationConverter() {
    return new DefaultCamundaAuthenticationConverter();
  }

  @Bean
  public CamundaAuthenticationProvider camundaAuthenticationProvider(
      final CamundaAuthenticationConverter<Authentication> converter) {
    return new DefaultCamundaAuthenticationProvider(converter);
  }

  @ConfigurationProperties("camunda.rest")
  public static final class GatewayRestProperties extends GatewayRestConfiguration {}
}
