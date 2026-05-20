/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.config;

import io.camunda.zeebe.gateway.rest.context.PhysicalTenantContext;
import io.camunda.zeebe.gateway.rest.filter.PhysicalTenantRoutingFilter;
import io.camunda.zeebe.gateway.rest.interceptor.PhysicalTenantInterceptor;
import io.camunda.zeebe.gateway.rest.resolver.PhysicalTenantIdArgumentResolver;
import io.camunda.zeebe.gateway.rest.util.PhysicalTenantRegistry;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Wires the physical-tenant aware routing into Spring MVC.
 *
 * <p>{@link PhysicalTenantRoutingFilter} rewrites {@code /v2/physical-tenants/{tenantId}/...}
 * requests to {@code /v2/...} before handler resolution, keeping Spring's handler mapping table
 * lean and preventing the duplicate "Mapped to ..." DEBUG log that a dual-registration approach
 * would cause.
 */
@Configuration
public class PhysicalTenantWebMvcConfig implements WebMvcConfigurer {

  private final PhysicalTenantInterceptor interceptor;

  public PhysicalTenantWebMvcConfig(final ObjectProvider<PhysicalTenantRegistry> registryProvider) {
    final PhysicalTenantRegistry registry =
        registryProvider.getIfAvailable(
            () -> PhysicalTenantContext.DEFAULT_PHYSICAL_TENANT_ID::equals);
    interceptor = new PhysicalTenantInterceptor(registry);
  }

  @Bean
  public FilterRegistrationBean<PhysicalTenantRoutingFilter> physicalTenantRoutingFilter() {
    final FilterRegistrationBean<PhysicalTenantRoutingFilter> registration =
        new FilterRegistrationBean<>();
    registration.setFilter(new PhysicalTenantRoutingFilter());
    registration.addUrlPatterns("/v2/*");
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return registration;
  }

  @Override
  public void addInterceptors(final InterceptorRegistry registry) {
    // Run early so downstream interceptors and controllers can read the resolved tenant id.
    registry.addInterceptor(interceptor).order(Integer.MIN_VALUE);
  }

  @Override
  public void addArgumentResolvers(final List<HandlerMethodArgumentResolver> resolvers) {
    // Enables `@PhysicalTenantId String physicalTenantId` injection on controller methods.
    resolvers.add(new PhysicalTenantIdArgumentResolver());
  }
}
