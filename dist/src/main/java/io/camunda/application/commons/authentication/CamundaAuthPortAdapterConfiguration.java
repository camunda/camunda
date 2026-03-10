/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.authentication;

import io.camunda.application.commons.authentication.adapter.OcAuthorizationManagementAdapter;
import io.camunda.application.commons.authentication.adapter.OcGroupManagementAdapter;
import io.camunda.application.commons.authentication.adapter.OcMappingRuleManagementAdapter;
import io.camunda.application.commons.authentication.adapter.OcRoleManagementAdapter;
import io.camunda.application.commons.authentication.adapter.OcTenantManagementAdapter;
import io.camunda.application.commons.authentication.adapter.OcUserManagementAdapter;
import io.camunda.application.commons.condition.ConditionalOnAnyHttpGatewayEnabled;
import io.camunda.auth.domain.port.inbound.AuthorizationManagementPort;
import io.camunda.auth.domain.port.inbound.GroupManagementPort;
import io.camunda.auth.domain.port.inbound.MappingRuleManagementPort;
import io.camunda.auth.domain.port.inbound.RoleManagementPort;
import io.camunda.auth.domain.port.inbound.TenantManagementPort;
import io.camunda.auth.domain.port.inbound.UserManagementPort;
import io.camunda.auth.domain.spi.CamundaAuthenticationProvider;
import io.camunda.auth.starter.condition.ConditionalOnCamundaGroupsEnabled;
import io.camunda.auth.starter.condition.ConditionalOnInternalUserManagement;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.GroupServices;
import io.camunda.service.MappingRuleServices;
import io.camunda.service.RoleServices;
import io.camunda.service.TenantServices;
import io.camunda.service.UserServices;
import io.camunda.spring.utils.ConditionalOnSecondaryStorageEnabled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers OC (Orchestration Cluster) adapter beans that bridge between the auth library's inbound
 * ports and the monorepo's {@code *Services} classes. These adapters delegate write operations to
 * Zeebe via the broker client and read operations to the search infrastructure, translating between
 * auth library domain models and monorepo entities.
 *
 * <p>This configuration is active only when secondary storage (ES/RDBMS) is enabled, meaning the
 * monorepo operates in "external" persistence mode where Zeebe exports data and the auth library
 * reads from it.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "camunda.auth.method")
@ConditionalOnAnyHttpGatewayEnabled
@ConditionalOnSecondaryStorageEnabled
public class CamundaAuthPortAdapterConfiguration {

  @Bean
  @ConditionalOnInternalUserManagement
  UserManagementPort ocUserManagementPort(
      final UserServices userServices, final CamundaAuthenticationProvider authProvider) {
    return new OcUserManagementAdapter(userServices, authProvider);
  }

  @Bean
  RoleManagementPort ocRoleManagementPort(
      final RoleServices roleServices,
      final MappingRuleServices mappingRuleServices,
      final CamundaAuthenticationProvider authProvider) {
    return new OcRoleManagementAdapter(roleServices, mappingRuleServices, authProvider);
  }

  @Bean
  @ConditionalOnCamundaGroupsEnabled
  GroupManagementPort ocGroupManagementPort(
      final GroupServices groupServices,
      final MappingRuleServices mappingRuleServices,
      final CamundaAuthenticationProvider authProvider) {
    return new OcGroupManagementAdapter(groupServices, mappingRuleServices, authProvider);
  }

  @Bean
  TenantManagementPort ocTenantManagementPort(
      final TenantServices tenantServices,
      final MappingRuleServices mappingRuleServices,
      final CamundaAuthenticationProvider authProvider) {
    return new OcTenantManagementAdapter(tenantServices, mappingRuleServices, authProvider);
  }

  @Bean
  MappingRuleManagementPort ocMappingRuleManagementPort(
      final MappingRuleServices mappingRuleServices,
      final CamundaAuthenticationProvider authProvider) {
    return new OcMappingRuleManagementAdapter(mappingRuleServices, authProvider);
  }

  @Bean
  AuthorizationManagementPort ocAuthorizationManagementPort(
      final AuthorizationServices authorizationServices,
      final CamundaAuthenticationProvider authProvider) {
    return new OcAuthorizationManagementAdapter(authorizationServices, authProvider);
  }
}
