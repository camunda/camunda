/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.pt;

import io.camunda.application.commons.identity.PhysicalTenantsConfiguredCondition;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;

/**
 * Wires the three host-side pieces that make plain Spring MVC webapp controllers (Operate,
 * Tasklist, Admin) reachable under the {@code /physical-tenant/<id>/...} access path:
 *
 * <ol>
 *   <li>{@link PhysicalTenantWebappRequestMappingHandlerMapping} — registers PT-prefixed siblings
 *       of every webapp controller mapping so URLs like {@code
 *       /physical-tenant/<id>/operate/client-config.js} actually have a handler.
 *   <li>{@link PhysicalTenantWebappContextPathInterceptor} — rewrites the {@code contextPath} model
 *       attribute that index controllers emit, so the rendered SPA's base URL stays inside the PT
 *       prefix.
 *   <li>A dedicated {@link SimpleUrlHandlerMapping} (with {@link ResourceHttpRequestHandler}
 *       handlers) so static asset URLs like {@code /physical-tenant/<id>/operate/assets/index.js}
 *       resolve to the same classpath locations Spring Boot's default handler serves the unprefixed
 *       assets from. The earlier {@code WebMvcConfigurer.addResourceHandlers} approach did not take
 *       effect in this stack; declaring a {@code SimpleUrlHandlerMapping} bean directly mirrors the
 *       {@code @Bean} path the {@link PhysicalTenantWebappRequestMappingHandlerMapping} uses and
 *       works.
 * </ol>
 *
 * <p>All pieces are conditional on {@code camunda.physical-tenants.*} being configured.
 */
@Configuration(proxyBeanMethods = false)
@Conditional(PhysicalTenantsConfiguredCondition.class)
public class PhysicalTenantWebappMappingConfiguration implements WebMvcConfigurer {

  private static final List<String> WEBAPP_NAMES = List.of("operate", "tasklist", "admin");

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

  /**
   * Direct {@link SimpleUrlHandlerMapping} that serves PT-prefixed static assets from the same
   * classpath roots the unprefixed assets come from. Declared as a {@code @Bean} rather than via
   * {@link #addResourceHandlers} because that method's contribution to the auto-configured resource
   * handler mapping was being silently dropped in this stack — registering a {@code
   * SimpleUrlHandlerMapping} bean directly bypasses the {@code
   * WebMvcConfigurationSupport.resourceHandlerMapping} pipeline and gives us deterministic pattern
   * + precedence wiring (we own the order).
   */
  @Bean
  public SimpleUrlHandlerMapping physicalTenantWebappResourceHandlerMapping() {
    final Map<String, ResourceHttpRequestHandler> urlMap = new LinkedHashMap<>();
    for (final String app : WEBAPP_NAMES) {
      final var handler = new ResourceHttpRequestHandler();
      handler.setLocations(List.of(new ClassPathResource("/META-INF/resources/" + app + "/")));
      // ResourceHttpRequestHandler is normally a Spring bean and gets afterPropertiesSet() via
      // the bean lifecycle. We instantiate it manually here, so call it explicitly to install the
      // default PathResourceResolver and resolve the allowed locations.
      try {
        handler.afterPropertiesSet();
      } catch (final Exception e) {
        throw new IllegalStateException(
            "Failed to initialise PT webapp resource handler for '" + app + "'", e);
      }
      urlMap.put("/physical-tenant/*/" + app + "/**", handler);
    }
    final var mapping = new SimpleUrlHandlerMapping(urlMap);
    // Run between our custom RMHM (HIGHEST_PRECEDENCE + 10) and the PT REST RMHM / default
    // resource handler. Patterns are disjoint from any other handler mapping's, so the absolute
    // value only matters relative to itself; pick a stable medium-high precedence.
    mapping.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
    return mapping;
  }

  @Override
  public void addInterceptors(final InterceptorRegistry registry) {
    registry.addInterceptor(physicalTenantWebappContextPathInterceptor());
  }
}
