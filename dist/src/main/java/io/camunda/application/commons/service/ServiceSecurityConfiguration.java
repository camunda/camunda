/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.service;

import io.camunda.application.commons.service.ServiceSecurityConfiguration.ServiceSecurityProperties;
import io.camunda.search.clients.AuthorizationSearchClient;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.impl.AuthorizationChecker;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.gateway.rest.ConditionalOnRestGatewayEnabled;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnRestGatewayEnabled
@EnableConfigurationProperties(ServiceSecurityProperties.class)
public class ServiceSecurityConfiguration {

  @Bean
  public SecurityContextProvider securityContextProvider(
      final ServiceSecurityProperties serviceSecurityProperties,
      final AuthorizationChecker authorizationChecker) {
    return new SecurityContextProvider(serviceSecurityProperties, authorizationChecker);
  }

  @Bean
  public AuthorizationChecker authorizationChecker(
      final AuthorizationSearchClient authorizationSearchClient) {
    return new AuthorizationChecker(authorizationSearchClient);
  }

  @ConfigurationProperties("camunda.security")
  public static final class ServiceSecurityProperties extends SecurityConfiguration {}
}
