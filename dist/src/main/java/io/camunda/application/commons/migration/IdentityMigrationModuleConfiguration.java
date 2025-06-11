/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.migration;

import io.camunda.application.commons.configuration.GatewayBasedConfiguration;
import io.camunda.search.clients.AuthorizationSearchClient;
import io.camunda.search.clients.GroupSearchClient;
import io.camunda.search.clients.MappingSearchClient;
import io.camunda.search.clients.RoleSearchClient;
import io.camunda.search.clients.SearchClientsProxy;
import io.camunda.search.clients.TenantSearchClient;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.impl.AuthorizationChecker;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.GroupServices;
import io.camunda.service.MappingServices;
import io.camunda.service.RoleServices;
import io.camunda.service.TenantServices;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@ComponentScan(
    basePackages = {
      "io.camunda.migration.identity",
      // broker client setup requires actor and clustering setup as well
      "io.camunda.application.commons.actor",
      "io.camunda.application.commons.broker.client",
      "io.camunda.application.commons.clustering",
      // security setup is needed for service layer
      "io.camunda.application.commons.security"
    })
@Profile("identity-migration")
@EnableAutoConfiguration
@Import({GatewayBasedConfiguration.class})
public class IdentityMigrationModuleConfiguration {
  @Bean
  public AuthorizationServices authorizationServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final AuthorizationSearchClient authorizationSearchClient,
      final SecurityConfiguration securityConfiguration) {
    return new AuthorizationServices(
        brokerClient,
        securityContextProvider,
        authorizationSearchClient,
        null,
        securityConfiguration);
  }

  @Bean
  public GroupServices groupServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final GroupSearchClient groupSearchClient) {
    return new GroupServices(brokerClient, securityContextProvider, groupSearchClient, null);
  }

  @Bean
  public MappingServices mappingServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final MappingSearchClient mappingSearchClient) {
    return new MappingServices(brokerClient, securityContextProvider, mappingSearchClient, null);
  }

  @Bean
  public RoleServices roleServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final RoleSearchClient roleSearchClient) {
    return new RoleServices(brokerClient, securityContextProvider, roleSearchClient, null);
  }

  @Bean
  public TenantServices tenantServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final TenantSearchClient tenantSearchClient) {
    return new TenantServices(brokerClient, securityContextProvider, tenantSearchClient, null);
  }

  @Bean
  public SearchClientsProxy noopSearchClientsProxy() {
    return SearchClientsProxy.noop();
  }

  @Bean
  public SecurityContextProvider securityContextProvider(
      final SecurityConfiguration securityConfiguration,
      final AuthorizationChecker authorizationChecker) {
    return new SecurityContextProvider(securityConfiguration, authorizationChecker);
  }

  @Bean
  public AuthorizationChecker authorizationChecker(
      final AuthorizationSearchClient authorizationSearchClient) {
    return new AuthorizationChecker(authorizationSearchClient);
  }
}
