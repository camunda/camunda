/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config.controllers;

import io.camunda.authentication.service.DefaultMembershipService;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import io.camunda.service.ApiServicesExecutorProvider;
import io.camunda.service.GroupServices;
import io.camunda.service.MappingRuleServices;
import io.camunda.service.RoleServices;
import io.camunda.service.TenantServices;
import io.camunda.service.registry.DefaultServiceRegistry;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/** Additional dependency beans for the OIDC setup */
@Configuration
public class WebSecurityOidcTestContext {

  /**
   * No-op {@link JwtDecoder} mock. These tests configure a non-functional JWK URI (e.g. {@code
   * jwks.example.com}) and do not exercise JWT validation — they test filter-chain behaviour only.
   * CSL's {@code @ConditionalOnMissingBean} default backs off when this bean is present.
   */
  @Bean
  public JwtDecoder testJwtDecoder() {
    return Mockito.mock(JwtDecoder.class);
  }

  @Bean
  public MappingRuleServices createMappingRuleServices(
      final ApiServicesExecutorProvider executorProvider) {
    return new MappingRuleServices("default", null, null, null, executorProvider, null);
  }

  @Bean
  public DefaultMembershipService createMembershipService(
      final MappingRuleServices mappingRuleServices,
      final TenantServices tenantServices,
      final RoleServices roleServices,
      final GroupServices groupServices,
      final CamundaSecurityLibraryProperties cslProperties) {
    final var serviceRegistry =
        DefaultServiceRegistry.of(
            b ->
                b.mappingRuleServices("default", mappingRuleServices)
                    .tenantServices("default", tenantServices)
                    .roleServices("default", roleServices)
                    .groupServices("default", groupServices));
    return new DefaultMembershipService(serviceRegistry, cslProperties);
  }
}
