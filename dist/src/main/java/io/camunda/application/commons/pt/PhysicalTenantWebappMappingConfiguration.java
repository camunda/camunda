/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.pt;

import io.camunda.application.commons.identity.PhysicalTenantsConfiguredCondition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Wires the host-side pieces that make plain Spring MVC webapp controllers (Operate, Tasklist,
 * Admin) reachable under the {@code /physical-tenant/<id>/...} access path:
 *
 * <ol>
 *   <li>{@link PhysicalTenantWebappRequestMappingHandlerMapping} — registers PT-prefixed siblings
 *       of every webapp controller mapping so URLs like {@code
 *       /physical-tenant/<id>/operate/client-config.js} actually have a handler.
 *   <li>{@link PhysicalTenantWebappContextPathInterceptor} — rewrites the {@code contextPath} model
 *       attribute that index controllers emit, so the rendered SPA's base URL stays inside the PT
 *       prefix.
 *   <li>{@link PhysicalTenantWebappAssetController} (separate {@code @Controller} bean) — forwards
 *       PT-prefixed static asset URLs to the unprefixed equivalents so Spring Boot's default
 *       resource handler serves them. Earlier attempts at a parallel {@code
 *       SimpleUrlHandlerMapping} and a {@code WebMvcConfigurer.addResourceHandlers} contribution
 *       silently failed to match in this stack; the controller {@code "forward:"} is short and
 *       reliable.
 * </ol>
 *
 * <p>All pieces are conditional on {@code camunda.physical-tenants.*} being configured.
 */
@Configuration(proxyBeanMethods = false)
@Conditional(PhysicalTenantsConfiguredCondition.class)
public class PhysicalTenantWebappMappingConfiguration implements WebMvcConfigurer {

  @Bean
  public PhysicalTenantWebappContextPathInterceptor physicalTenantWebappContextPathInterceptor() {
    return new PhysicalTenantWebappContextPathInterceptor();
  }

  /**
   * Our custom {@link PhysicalTenantWebappRequestMappingHandlerMapping} does NOT get interceptors
   * auto-applied — Spring's {@code WebMvcConfigurationSupport} only calls {@code
   * setInterceptors(...)} on the auto-configured handler mapping. We therefore install the PT
   * context-path interceptor on this RMHM explicitly. The same interceptor is ALSO registered
   * globally via {@link #addInterceptors} so it fires on the default RMHM too — needed because
   * {@code OperateIndexController#forwardToOperate} returns {@code "forward:/operate"} for SPA
   * sub-paths, and the forwarded {@code /operate} request is resolved by the default RMHM.
   */
  @Bean
  public PhysicalTenantWebappRequestMappingHandlerMapping
      physicalTenantWebappRequestMappingHandlerMapping(
          final PhysicalTenantWebappContextPathInterceptor contextPathInterceptor) {
    final var mapping = new PhysicalTenantWebappRequestMappingHandlerMapping();
    mapping.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
    mapping.setInterceptors(contextPathInterceptor);
    return mapping;
  }

  @Override
  public void addInterceptors(final InterceptorRegistry registry) {
    registry.addInterceptor(physicalTenantWebappContextPathInterceptor());
  }
}
