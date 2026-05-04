/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.config;

import io.camunda.zeebe.gateway.rest.context.PhysicalTenantContext;
import io.camunda.zeebe.gateway.rest.interceptor.PhysicalTenantInterceptor;
import io.camunda.zeebe.gateway.rest.mapper.PhysicalTenantRequestMappingHandlerMapping;
import io.camunda.zeebe.gateway.rest.util.PhysicalTenantResolver;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.webmvc.autoconfigure.WebMvcRegistrations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Wires the physical-tenant aware request mapping into Spring MVC.
 *
 * <p>{@link WebMvcRegistrations#getRequestMappingHandlerMapping()} is the supported extension point
 * for replacing the auto-configured {@link RequestMappingHandlerMapping}, so every future
 * {@code @CamundaRestController} automatically gets the {@code
 * /v2/physical-tenants/{physicalTenantId}/...} sibling registration.
 */
@Configuration
public class PhysicalTenantWebMvcConfig implements WebMvcConfigurer {

  private final PhysicalTenantInterceptor interceptor;

  public PhysicalTenantWebMvcConfig(final ObjectProvider<PhysicalTenantResolver> resolverProvider) {
    final PhysicalTenantResolver resolver =
        resolverProvider.getIfAvailable(
            () -> PhysicalTenantContext.DEFAULT_PHYSICAL_TENANT_ID::equals);
    interceptor = new PhysicalTenantInterceptor(resolver);
  }

  @Bean
  public WebMvcRegistrations physicalTenantWebMvcRegistrations() {
    return new WebMvcRegistrations() {
      @Override
      public RequestMappingHandlerMapping getRequestMappingHandlerMapping() {
        return new PhysicalTenantRequestMappingHandlerMapping();
      }
    };
  }

  @Override
  public void addInterceptors(final InterceptorRegistry registry) {
    // Run early so downstream interceptors and controllers can read the resolved tenant id.
    registry.addInterceptor(interceptor).order(Integer.MIN_VALUE);
  }
}
