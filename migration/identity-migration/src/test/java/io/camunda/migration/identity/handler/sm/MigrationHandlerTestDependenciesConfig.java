/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.handler.sm;

import static org.mockito.Mockito.mock;

import io.camunda.migration.identity.client.ManagementIdentityClient;
import io.camunda.migration.identity.config.IdentityMigrationProperties;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.GroupServices;
import io.camunda.service.MappingRuleServices;
import io.camunda.service.RoleServices;
import io.camunda.service.TenantServices;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class MigrationHandlerTestDependenciesConfig {

  @Bean
  public CamundaAuthentication camundaAuthentication() {
    return mock(CamundaAuthentication.class);
  }

  @Bean
  public ManagementIdentityClient managementIdentityClient() {
    return mock(ManagementIdentityClient.class);
  }

  @Bean
  public RoleServices roleServices() {
    return mock(RoleServices.class);
  }

  @Bean
  public AuthorizationServices authorizationServices() {
    return mock(AuthorizationServices.class);
  }

  @Bean
  public GroupServices groupServices() {
    return mock(GroupServices.class);
  }

  @Bean
  public TenantServices tenantServices() {
    return mock(TenantServices.class);
  }

  @Bean
  public IdentityMigrationProperties identityMigrationProperties() {
    return mock(IdentityMigrationProperties.class);
  }

  @Bean
  public MappingRuleServices mappingRuleServices() {
    return mock(MappingRuleServices.class);
  }
}
