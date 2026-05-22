/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.pt;

import io.camunda.application.commons.identity.PhysicalTenantsConfiguredCondition;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Wires the two host-side pieces that make plain Spring MVC webapp controllers (Operate, Tasklist,
 * Admin) reachable under the {@code /physical-tenant/<id>/...} access path:
 *
 * <ol>
 *   <li>{@link PhysicalTenantWebappRequestMappingHandlerMapping} — registers PT-prefixed siblings
 *       of every webapp controller mapping so URLs like {@code
 *       /physical-tenant/<id>/operate/client-config.js} actually have a handler.
 *   <li>{@link PhysicalTenantWebappContextPathInterceptor} — rewrites the {@code contextPath} model
 *       attribute that index controllers emit, so the rendered SPA's base URL stays inside the PT
 *       prefix (otherwise the SPA bootstraps from the unprefixed {@code /operate/} and drops out of
 *       the PT cookie scope).
 * </ol>
 *
 * <p>Both pieces are conditional on {@code camunda.physical-tenants.*} being configured (same gate
 * as the rest of the PT wiring, see {@link PhysicalTenantsConfiguredCondition}).
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
   * context-path interceptor on this RMHM explicitly via {@link
   * org.springframework.web.servlet.handler.AbstractHandlerMapping#setInterceptors}. The same
   * interceptor is ALSO registered globally via {@link #addInterceptors} so it fires on the default
   * RMHM too — needed because {@code OperateIndexController#forwardToOperate} returns {@code
   * "forward:/operate"} for SPA sub-paths, and the forwarded {@code /operate} request is resolved
   * by the default RMHM.
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

  /**
   * Maps PT-prefixed static-asset URLs to the same classpath locations Spring Boot's default
   * handler serves the unprefixed assets from. After {@link
   * PhysicalTenantWebappContextPathInterceptor} rewrites {@code <base href>} to {@code
   * /physical-tenant/<id>/operate/}, the SPA emits asset requests like {@code
   * /physical-tenant/<id>/operate/assets/index-xyz.js}; without this resource handler those URLs
   * 404 (no controller matches and the default static-resource handler is wired to {@code
   * /operate/**}).
   *
   * <p>Covers operate, tasklist, and admin in parallel with {@link
   * PhysicalTenantWebappRequestMappingHandlerMapping#WEBAPP_BASES} (kept in sync by convention;
   * both lists are small).
   */
  @Override
  public void addResourceHandlers(final ResourceHandlerRegistry registry) {
    // Use Ant-style "*" for the tenant id segment rather than the {tenantId} URI template form
    // — ResourceHandlerRegistry installs a SimpleUrlHandlerMapping whose pattern matching does
    // not treat the {var} placeholder as a wildcard the same way @RequestMapping does. "*" gives
    // us the one-segment match we want without forcing a path-variable extraction (we don't need
    // the value here; the security chain + interceptor already carry the tenant context).
    for (final String app : Set.of("operate", "tasklist", "admin")) {
      registry
          .addResourceHandler("/physical-tenant/*/" + app + "/**")
          .addResourceLocations("classpath:/META-INF/resources/" + app + "/");
    }
  }
}
