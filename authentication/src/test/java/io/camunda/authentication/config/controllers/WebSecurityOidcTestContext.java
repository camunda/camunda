/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config.controllers;

import io.camunda.authentication.ConditionalOnAuthenticationMethod;
import io.camunda.authentication.service.MembershipService;
import io.camunda.authentication.service.OidcMembershipService;
import io.camunda.security.api.model.config.AuthenticationMethod;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.service.ApiServicesExecutorProvider;
import io.camunda.service.GroupServices;
import io.camunda.service.MappingRuleServices;
import io.camunda.service.RoleServices;
import io.camunda.service.TenantServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Additional dependency beans for the OIDC setup */
@Configuration
@ConditionalOnAuthenticationMethod(AuthenticationMethod.OIDC)
public class WebSecurityOidcTestContext {

  @Bean
  public MappingRuleServices createMappingRuleServices(
      final ApiServicesExecutorProvider executorProvider) {
    return new MappingRuleServices(null, null, null, executorProvider, null);
  }

  @Bean
  public MembershipService createMembershipService(
      final MappingRuleServices mappingRuleServices,
      final TenantServices tenantServices,
      final RoleServices roleServices,
      final GroupServices groupServices,
      final SecurityConfiguration securityConfiguration) {
    return new OidcMembershipService(
        mappingRuleServices, tenantServices, roleServices, groupServices, securityConfiguration);
  }
}
