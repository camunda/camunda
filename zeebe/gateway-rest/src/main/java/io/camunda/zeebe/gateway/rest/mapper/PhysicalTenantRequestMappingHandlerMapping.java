/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.mapper;

import io.camunda.zeebe.gateway.rest.annotation.ClusterScoped;
import io.camunda.zeebe.gateway.rest.context.PhysicalTenantContext;
import io.camunda.zeebe.gateway.rest.controller.CamundaRestController;
import io.camunda.zeebe.gateway.rest.interceptor.PhysicalTenantInterceptor;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Custom {@link RequestMappingHandlerMapping} that, in addition to the original path declared on
 * each {@link CamundaRestController}, registers a tenant-prefixed variant under {@code
 * /v2/physical-tenants/{physicalTenantId}/...}.
 *
 * <p>Controllers annotated with {@link ClusterScoped} are skipped — their endpoints stay
 * cluster-level and remain reachable only under their original path.
 *
 * <p>Validation of the {@code physicalTenantId} captured by the prefix and propagation to the
 * request scope is performed by {@link PhysicalTenantInterceptor}.
 */
public class PhysicalTenantRequestMappingHandlerMapping extends RequestMappingHandlerMapping {

  /** Path segment inserted between {@code /v2} and the original resource path. */
  static final String PREFIX_SEGMENT =
      "physical-tenants/{" + PhysicalTenantContext.PATH_VARIABLE_PHYSICAL_TENANT_ID + "}";

  private static final String V2 = "/v2";

  @Override
  protected void registerHandlerMethod(
      final Object handler, final Method method, final RequestMappingInfo mapping) {
    super.registerHandlerMethod(handler, method, mapping);

    final Class<?> beanType = resolveBeanType(handler);
    if (beanType == null || !shouldPrefix(beanType)) {
      return;
    }

    final RequestMappingInfo prefixed = withPhysicalTenantPrefix(mapping);
    if (prefixed != null) {
      super.registerHandlerMethod(handler, method, prefixed);
    }
  }

  /** Visible for testing. */
  protected boolean shouldPrefix(final Class<?> beanType) {
    return AnnotatedElementUtils.hasAnnotation(beanType, CamundaRestController.class)
        && !AnnotatedElementUtils.hasAnnotation(beanType, ClusterScoped.class);
  }

  /** Visible for testing. */
  protected RequestMappingInfo withPhysicalTenantPrefix(final RequestMappingInfo mapping) {
    final Set<String> patterns = extractPatterns(mapping);
    final Set<String> prefixed = new LinkedHashSet<>();
    for (final String pattern : patterns) {
      final String rewritten = prefix(pattern);
      if (rewritten != null) {
        prefixed.add(rewritten);
      }
    }
    if (prefixed.isEmpty()) {
      return null;
    }
    return mapping.mutate().paths(prefixed.toArray(String[]::new)).build();
  }

  private Set<String> extractPatterns(final RequestMappingInfo mapping) {
    if (mapping.getPathPatternsCondition() != null) {
      return mapping.getPathPatternsCondition().getPatternValues();
    }
    return Set.of();
  }

  private String prefix(final String pattern) {
    if (pattern == null || !pattern.startsWith(V2)) {
      // Only /v2 routes participate in the physical-tenant addressing scheme.
      return null;
    }
    final String tail = pattern.substring(V2.length());
    if (tail.isEmpty()) {
      return V2 + "/" + PREFIX_SEGMENT;
    }
    if (!tail.startsWith("/")) {
      // Avoid prefixing things like "/v2foo".
      return null;
    }
    return V2 + "/" + PREFIX_SEGMENT + tail;
  }

  private Class<?> resolveBeanType(final Object handler) {
    if (handler instanceof final String beanName) {
      return obtainApplicationContext().getType(beanName);
    }
    return handler != null ? handler.getClass() : null;
  }
}
