/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.config;

import static io.camunda.security.api.model.config.MultiTenancyConfiguration.API_ENABLED_PROPERTY;
import static io.camunda.security.api.model.config.oidc.OidcConfiguration.GROUPS_CLAIM_PROPERTY;
import static io.camunda.spring.utils.PhysicalTenantContext.PHYSICAL_TENANTS_PATH_SEGMENT;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.security.api.model.config.AuthenticationMethod;
import io.camunda.security.spring.annotation.ConditionalOnAuthenticationMethod;
import io.camunda.zeebe.gateway.rest.controller.EndpointAccessErrorFilter;
import io.camunda.zeebe.gateway.rest.controller.PhysicalTenantFilter;
import io.camunda.zeebe.util.VisibleForTesting;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

@Configuration
public class ApiFiltersConfiguration {

  @VisibleForTesting
  public static final String GROUPS_API_DISABLED_ERROR_MESSAGE =
      "Groups API is disabled because the application is configured in group claim mode. To use the Groups API, reconfigure the application to manage group mode removing the '%s' property."
          .formatted(GROUPS_CLAIM_PROPERTY);

  @VisibleForTesting
  public static final String USERS_API_DISABLED_ERROR_MESSAGE =
      "Users API is disabled because the application is configured in OIDC mode.";

  @VisibleForTesting
  public static final String TENANTS_API_DISABLED_ERROR_MESSAGE =
      "Tenants API is disabled. Enable the API by setting '%s' to true."
          .formatted(API_ENABLED_PROPERTY);

  private static final PathPatternParser PATTERN_PARSER = new PathPatternParser();

  private static List<PathPattern> patterns(final String... patterns) {
    return Arrays.stream(patterns).map(PATTERN_PARSER::parse).toList();
  }

  // Spring Security's FilterChainProxy registers at SecurityProperties.DEFAULT_FILTER_ORDER (-100);
  // the physical-tenant filter must run just before it so components inside the security chain can
  // read the tenant id. We inline the value rather than depend on spring-boot-security for one
  // constant — and DEFAULT_FILTER_ORDER is a compile-time constant, so a reference would inline the
  // value anyway and leave the dependency flagged as unused by dependency:analyze. See ADR-0003.
  private static final int PHYSICAL_TENANT_FILTER_ORDER = -101;

  /**
   * Stamps the physical tenant id from {@code /physical-tenants/{id}/...} paths onto the request
   * before Spring Security runs, so the security chain and downstream handlers can read it.
   */
  @Bean
  public FilterRegistrationBean<PhysicalTenantFilter> physicalTenantFilter() {
    final FilterRegistrationBean<PhysicalTenantFilter> registration =
        new FilterRegistrationBean<>(new PhysicalTenantFilter());
    registration.addUrlPatterns(PHYSICAL_TENANTS_PATH_SEGMENT + "*");
    registration.setOrder(PHYSICAL_TENANT_FILTER_ORDER);
    return registration;
  }

  @ConditionalOnExpression("'${camunda.security.authentication.oidc.groupsClaim:}' != ''")
  @Bean
  public FilterRegistrationBean<EndpointAccessErrorFilter> disableGroupApiFilter(
      final ObjectMapper objectMapper) {
    final FilterRegistrationBean<EndpointAccessErrorFilter> registration =
        new FilterRegistrationBean<>();
    registration.setFilter(
        new EndpointAccessErrorFilter(
            objectMapper,
            GROUPS_API_DISABLED_ERROR_MESSAGE,
            patterns("/v2/groups/**", PHYSICAL_TENANTS_PATH_SEGMENT + "*/v2/groups/**")));
    registration.addUrlPatterns("/*");
    registration.setOrder(1);
    return registration;
  }

  @ConditionalOnAuthenticationMethod(AuthenticationMethod.OIDC)
  @Bean
  public FilterRegistrationBean<EndpointAccessErrorFilter> disableUserApiFilter(
      final ObjectMapper objectMapper) {
    final FilterRegistrationBean<EndpointAccessErrorFilter> registration =
        new FilterRegistrationBean<>();
    registration.setFilter(
        new EndpointAccessErrorFilter(
            objectMapper,
            USERS_API_DISABLED_ERROR_MESSAGE,
            patterns("/v2/users/**", PHYSICAL_TENANTS_PATH_SEGMENT + "*/v2/users/**")));
    registration.addUrlPatterns("/*");
    registration.setOrder(1);
    return registration;
  }

  @ConditionalOnExpression("'${camunda.security.multiTenancy.apiEnabled:}' == 'false'")
  @Bean
  public FilterRegistrationBean<EndpointAccessErrorFilter> disableMultiTenancyApiFilter(
      final ObjectMapper objectMapper) {
    final FilterRegistrationBean<EndpointAccessErrorFilter> registration =
        new FilterRegistrationBean<>();
    registration.setFilter(
        new EndpointAccessErrorFilter(
            objectMapper,
            TENANTS_API_DISABLED_ERROR_MESSAGE,
            patterns("/v2/tenants/**", PHYSICAL_TENANTS_PATH_SEGMENT + "*/v2/tenants/**")));
    registration.addUrlPatterns("/*");
    registration.setOrder(1);
    return registration;
  }
}
