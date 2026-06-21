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
import io.camunda.zeebe.gateway.rest.mapper.PhysicalTenantRequestMappingHandlerMapping;
import io.camunda.zeebe.gateway.rest.resolver.PhysicalTenantIdArgumentResolver;
import java.util.List;
import org.springframework.boot.webmvc.autoconfigure.WebMvcRegistrations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
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
 * <p>This config is responsible for <em>routing</em> only. The physical tenant id is extracted from
 * the request by {@code PhysicalTenantFilter} (which runs before the security chain so the id is
 * available to in-chain components), and unknown tenants are rejected by CSL's catch-all security
 * chain — so no MVC interceptor is registered here. See ADR-0003.
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
  public void addResourceHandlers(final ResourceHandlerRegistry registry) {
    // Serve static assets and favicons for each webapp under the physical-tenant prefix, using the
    // same no-explicit-cache-control policy as the non-PT paths (Spring Boot's default classpath
    // handler). Two handlers per webapp:
    //   assets/**  — hash-suffixed filenames served from the webapp's assets/ classpath dir.
    //   *.ico      — favicon.ico at the webapp root; needs an explicit handler because all three
    //                index controllers exclude *.ico from SPA forwarding so it can be served
    //                statically — the default Spring handler resolves /operate/favicon.ico from
    //                classpath, but /physical-tenants/<id>/operate/favicon.ico needs this entry.
    for (final String webapp : WebAppProviderAdapter.WEB_APPS) {
      registry
          .addResourceHandler(
              PhysicalTenantContext.PHYSICAL_TENANTS_PATH_SEGMENT + "*/" + webapp + "/assets/**")
          .addResourceLocations(CLASSPATH_RESOURCES + webapp + "/assets/");
      registry
          .addResourceHandler(
              PhysicalTenantContext.PHYSICAL_TENANTS_PATH_SEGMENT + "*/" + webapp + "/*.ico")
          .addResourceLocations(CLASSPATH_RESOURCES + webapp + "/");
    }
  }
}
