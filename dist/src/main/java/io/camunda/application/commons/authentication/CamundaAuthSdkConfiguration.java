/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.authentication;

import io.camunda.application.commons.condition.ConditionalOnAnyHttpGatewayEnabled;
import io.camunda.application.commons.condition.ConditionalOnSecondaryStorageDisabled;
import io.camunda.auth.domain.spi.MembershipResolver;
import io.camunda.auth.domain.spi.TenantInfoProvider;
import io.camunda.auth.domain.spi.UserProfileProvider;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.service.GroupServices;
import io.camunda.service.MappingRuleServices;
import io.camunda.service.RoleServices;
import io.camunda.service.TenantServices;
import io.camunda.service.UserServices;
import io.camunda.spring.utils.ConditionalOnSecondaryStorageEnabled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Registers consumer-specific SPI implementations for the auth library. These replace the starter's
 * defaults (e.g., {@code NoOpMembershipResolver}) via {@code @ConditionalOnMissingBean} because
 * they are defined before auto-configuration runs.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "camunda.auth.method")
@ConditionalOnAnyHttpGatewayEnabled
@ComponentScan(basePackages = {"io.camunda.service.validation"})
public class CamundaAuthSdkConfiguration {

  @Bean
  @ConditionalOnSecondaryStorageEnabled
  public MembershipResolver camundaMembershipResolver(
      final MappingRuleServices mappingRuleServices,
      final TenantServices tenantServices,
      final RoleServices roleServices,
      final GroupServices groupServices,
      final SecurityConfiguration securityConfiguration) {
    return new CamundaMembershipResolver(
        mappingRuleServices, tenantServices, roleServices, groupServices, securityConfiguration);
  }

  @Bean
  @ConditionalOnSecondaryStorageDisabled
  public MembershipResolver camundaNoDBMembershipResolver(
      final SecurityConfiguration securityConfiguration) {
    return new CamundaNoDBMembershipResolver(securityConfiguration);
  }

  @Bean
  public UserProfileProvider camundaUserProfileProvider(final UserServices userServices) {
    return new CamundaUserProfileProvider(userServices);
  }

  @Bean
  public TenantInfoProvider camundaTenantInfoProvider(final TenantServices tenantServices) {
    return new CamundaTenantInfoProvider(tenantServices);
  }
}
