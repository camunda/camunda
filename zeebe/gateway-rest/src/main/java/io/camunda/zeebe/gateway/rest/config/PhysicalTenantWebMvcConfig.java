/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.config;

import io.camunda.authentication.config.spi.WebAppProviderAdapter;
import io.camunda.spring.utils.PhysicalTenantContext;
import io.camunda.spring.utils.SegmentStrippingResolver;
import io.camunda.zeebe.gateway.rest.mapper.PhysicalTenantRequestMappingHandlerMapping;
import io.camunda.zeebe.gateway.rest.resolver.PhysicalTenantIdArgumentResolver;
import java.util.List;
import org.springframework.boot.webmvc.autoconfigure.WebMvcRegistrations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Wires the physical-tenant aware request mapping and static-asset serving into Spring MVC.
 *
 * <p>{@link WebMvcRegistrations#getRequestMappingHandlerMapping()} is the supported extension point
 * for replacing the auto-configured {@link RequestMappingHandlerMapping}, so every controller whose
 * routes start with a PT-addressable root ({@code /v2}, {@code /operate}, {@code /tasklist}, {@code
 * /admin}, {@code /webapp}) automatically gets a {@code /physical-tenants/{physicalTenantId}/...}
 * sibling registration.
 *
 * <p>The physical tenant id is extracted from the request by {@code PhysicalTenantFilter} (which
 * runs before the security chain so the id is available to in-chain components), and unknown
 * tenants are rejected by CSL's catch-all security chain (see ADR-0003). Alongside routing and
 * static assets, {@link PhysicalTenantWebappContextPathInterceptor} is registered so a PT-prefixed
 * request renders an SPA shell whose {@code <base href>} carries the tenant prefix.
 */
@Configuration
public class PhysicalTenantWebMvcConfig implements WebMvcConfigurer {

  private static final String CLASSPATH_RESOURCES = "classpath:/META-INF/resources/";

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
  public void addArgumentResolvers(final List<HandlerMethodArgumentResolver> resolvers) {
    // Enables `@PhysicalTenantId String physicalTenantId` injection on controller methods.
    resolvers.add(new PhysicalTenantIdArgumentResolver());
  }

  @Override
  public void addInterceptors(final InterceptorRegistry registry) {
    // Always wired; no-op for cluster (unprefixed) requests — PT addressing is decided per request
    // from PhysicalTenantContext, not by a boot-time condition.
    registry.addInterceptor(new PhysicalTenantWebappContextPathInterceptor());
  }

  @Override
  public void addResourceHandlers(final ResourceHandlerRegistry registry) {
    // *.ico needs its own handler: index controllers exclude it from SPA forwarding so it can be
    // served statically, but the default classpath handler only resolves the unprefixed path.
    // PathPattern.extractPathWithinPattern() starts at the first '*' (the tenant segment), so the
    // resolver receives "{tenant}/{webapp}/assets/file.js" (resp. "{tenant}/{webapp}/file.ico");
    // SegmentStrippingResolver drops those leading segments to resolve against the classpath root.
    for (final String webapp : WebAppProviderAdapter.WEB_APPS) {
      registry
          .addResourceHandler(
              PhysicalTenantContext.PHYSICAL_TENANTS_PATH_SEGMENT + "*/" + webapp + "/assets/**")
          .addResourceLocations(CLASSPATH_RESOURCES + webapp + "/assets/")
          .resourceChain(false)
          .addResolver(new SegmentStrippingResolver(3));
      registry
          .addResourceHandler(
              PhysicalTenantContext.PHYSICAL_TENANTS_PATH_SEGMENT + "*/" + webapp + "/*.ico")
          .addResourceLocations(CLASSPATH_RESOURCES + webapp + "/")
          .resourceChain(false)
          .addResolver(new SegmentStrippingResolver(2));
    }
  }
}
