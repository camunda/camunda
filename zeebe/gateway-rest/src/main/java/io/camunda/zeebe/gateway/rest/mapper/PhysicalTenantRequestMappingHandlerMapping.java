/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.mapper;

import io.camunda.gateway.mapping.http.physicaltenants.PhysicalTenantContext;
import io.camunda.zeebe.gateway.rest.annotation.ClusterScoped;
import io.camunda.zeebe.gateway.rest.controller.CamundaRestController;
import io.camunda.zeebe.util.VisibleForTesting;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

public class PhysicalTenantRequestMappingHandlerMapping extends RequestMappingHandlerMapping {

  /** Path segment prefixed before {@code /v2} in the physical-tenant addressing scheme. */
  private static final String PREFIX_SEGMENT =
      "physical-tenants/{" + PhysicalTenantContext.PATH_VARIABLE_PHYSICAL_TENANT_ID + "}";

  private static final String V2 = "/v2";

  @Override
  protected void registerHandlerMethod(
      final Object handler, final Method method, final RequestMappingInfo mapping) {
    // this is needed to keep original routes, without prefixes
    super.registerHandlerMethod(handler, method, mapping);

    final Class<?> beanType = resolveBeanType(handler);
    if (!shouldPrefix(beanType)) {
      return;
    }

    final RequestMappingInfo prefixed = withPhysicalTenantPrefix(mapping);
    if (prefixed != null) {
      super.registerHandlerMethod(handler, method, prefixed);
    }
  }

  @VisibleForTesting
  boolean shouldPrefix(final Class<?> beanType) {
    return beanType != null
        && AnnotatedElementUtils.hasAnnotation(beanType, CamundaRestController.class)
        && !AnnotatedElementUtils.hasAnnotation(beanType, ClusterScoped.class);
  }

  @VisibleForTesting
  protected RequestMappingInfo withPhysicalTenantPrefix(final RequestMappingInfo mapping) {
    final Set<String> prefixedPaths = buildPrefixedPaths(mapping);
    return prefixedPaths.isEmpty()
        ? null
        : mapping.mutate().paths(prefixedPaths.toArray(String[]::new)).build();
  }

  private Set<String> buildPrefixedPaths(final RequestMappingInfo mapping) {
    final Set<String> result = new LinkedHashSet<>();

    for (final String pattern : extractPatterns(mapping)) {
      final String prefixed = prefix(pattern);
      if (prefixed != null) {
        result.add(prefixed);
      }
    }

    return result;
  }

  private Set<String> extractPatterns(final RequestMappingInfo mapping) {
    final var condition = mapping.getPathPatternsCondition();
    return condition != null ? condition.getPatternValues() : Set.of();
  }

  private String prefix(final String pattern) {
    if (pattern == null || !pattern.startsWith(V2)) {
      // Only /v2 routes participate in the physical-tenant addressing scheme.
      return null;
    }
    final String tail = pattern.substring(V2.length());
    if (!tail.isEmpty() && !tail.startsWith("/")) {
      // Avoid prefixing things like "/v2foo".
      return null;
    }
    return "/" + PREFIX_SEGMENT + pattern;
  }

  private Class<?> resolveBeanType(final Object handler) {
    if (handler instanceof final String beanName) {
      return obtainApplicationContext().getType(beanName);
    }
    return handler != null ? handler.getClass() : null;
  }
}
