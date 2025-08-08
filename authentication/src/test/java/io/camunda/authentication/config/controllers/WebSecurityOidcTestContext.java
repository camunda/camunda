/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config.controllers;

import io.camunda.authentication.service.DefaultMembershipService;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.service.GroupServices;
import io.camunda.service.MappingRuleServices;
import io.camunda.service.RoleServices;
import io.camunda.service.TenantServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Additional dependency beans for the OIDC setup */
@Configuration
public class WebSecurityOidcTestContext {

  @Bean
  public MappingRuleServices createMappingRuleServices() {
    return new MappingRuleServices(null, null, null, null, null);
  }

  @Bean
  public DefaultMembershipService createMembershipService(
      final MappingRuleServices mappingRuleServices,
      final TenantServices tenantServices,
      final RoleServices roleServices,
      final GroupServices groupServices,
      final SecurityConfiguration securityConfiguration) {
    return new DefaultMembershipService(
        mappingRuleServices, tenantServices, roleServices, groupServices, securityConfiguration);
  }
}
