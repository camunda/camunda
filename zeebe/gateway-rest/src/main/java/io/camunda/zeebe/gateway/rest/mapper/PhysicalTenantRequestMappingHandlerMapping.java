/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.mapper;

import static io.camunda.spring.utils.PhysicalTenantContext.PHYSICAL_TENANT_URI_PREFIX;

import io.camunda.zeebe.gateway.rest.annotation.ClusterScoped;
import io.camunda.zeebe.util.VisibleForTesting;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

public class PhysicalTenantRequestMappingHandlerMapping extends RequestMappingHandlerMapping {

  private static final Set<String> PREFIXABLE_ROOTS =
      Set.of("/v2", "/operate", "/tasklist", "/admin", "/webapp");

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
    return beanType != null && !AnnotatedElementUtils.hasAnnotation(beanType, ClusterScoped.class);
  }

  @VisibleForTesting
  RequestMappingInfo withPhysicalTenantPrefix(final RequestMappingInfo mapping) {
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
    if (pattern == null) {
      return null;
    }
    for (final String root : PREFIXABLE_ROOTS) {
      if (pattern.equals(root) || pattern.startsWith(root + "/")) {
        return PHYSICAL_TENANT_URI_PREFIX + pattern;
      }
    }
    return null;
  }

  private Class<?> resolveBeanType(final Object handler) {
    if (handler instanceof final String beanName) {
      return obtainApplicationContext().getType(beanName);
    }
    return handler != null ? handler.getClass() : null;
  }
}
