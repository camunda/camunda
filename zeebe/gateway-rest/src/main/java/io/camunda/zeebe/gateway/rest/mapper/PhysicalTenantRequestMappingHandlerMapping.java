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
import io.camunda.zeebe.util.VisibleForTesting;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Custom {@link RequestMappingHandlerMapping} that, in addition to the original path declared on
 * each {@link CamundaRestController}, registers two tenant-prefixed variants (spec D7):
 *
 * <ul>
 *   <li>{@code /v2/physical-tenants/{physicalTenantId}/...} — direct API-client addressing
 *       (existing, REST-conventional shape).
 *   <li>{@code /physical-tenant/{physicalTenantId}/v2/...} — webapp/SPA addressing, aligned with
 *       the per-tenant webapp session cookie's {@code Path=/physical-tenant/<id>} scope so a
 *       browser-side session cookie covers both webapp and API URLs of the same tenant.
 * </ul>
 *
 * <p>Both prefixed variants use the same URI template variable name {@link
 * PhysicalTenantContext#PATH_VARIABLE_PHYSICAL_TENANT_ID}, so the same {@link
 * PhysicalTenantInterceptor} resolves the tenant id regardless of which prefix the client used.
 *
 * <p>Controllers annotated with {@link ClusterScoped} are skipped — their endpoints stay
 * cluster-level and remain reachable only under their original path.
 */
public class PhysicalTenantRequestMappingHandlerMapping extends RequestMappingHandlerMapping {

  private static final String V2 = "/v2";

  /** URI template path variable expression, e.g. {@code {physicalTenantId}}. */
  private static final String TENANT_VAR =
      "{" + PhysicalTenantContext.PATH_VARIABLE_PHYSICAL_TENANT_ID + "}";

  @Override
  protected void registerHandlerMethod(
      final Object handler, final Method method, final RequestMappingInfo mapping) {
    // this is needed to keep original routes, without prefixes
    super.registerHandlerMethod(handler, method, mapping);

    final Class<?> beanType = resolveBeanType(handler);
    if (beanType == null || !shouldPrefix(beanType)) {
      return;
    }

    for (final RequestMappingInfo prefixed : withPhysicalTenantPrefixes(mapping)) {
      super.registerHandlerMethod(handler, method, prefixed);
    }
  }

  @VisibleForTesting
  boolean shouldPrefix(final Class<?> beanType) {
    return AnnotatedElementUtils.hasAnnotation(beanType, CamundaRestController.class)
        && !AnnotatedElementUtils.hasAnnotation(beanType, ClusterScoped.class);
  }

  /**
   * Returns one {@link RequestMappingInfo} per prefixed scheme (D7). Each returned mapping has the
   * full set of original patterns rewritten under one of the two prefixes. Returns an empty list if
   * no original pattern is eligible for prefixing (i.e. nothing starts with {@code /v2}).
   */
  @VisibleForTesting
  List<RequestMappingInfo> withPhysicalTenantPrefixes(final RequestMappingInfo mapping) {
    final Set<String> patterns = extractPatterns(mapping);
    final Set<String> apiClientScheme = new LinkedHashSet<>();
    final Set<String> webappAlignedScheme = new LinkedHashSet<>();
    for (final String pattern : patterns) {
      final List<String> rewritten = prefixes(pattern);
      if (rewritten.isEmpty()) {
        continue;
      }
      // prefixes() always returns the two schemes in a fixed order: [api-client, webapp-aligned].
      apiClientScheme.add(rewritten.get(0));
      webappAlignedScheme.add(rewritten.get(1));
    }
    if (apiClientScheme.isEmpty()) {
      return List.of();
    }
    return List.of(
        mapping.mutate().paths(apiClientScheme.toArray(String[]::new)).build(),
        mapping.mutate().paths(webappAlignedScheme.toArray(String[]::new)).build());
  }

  private Set<String> extractPatterns(final RequestMappingInfo mapping) {
    if (mapping.getPathPatternsCondition() != null) {
      return mapping.getPathPatternsCondition().getPatternValues();
    }
    return Set.of();
  }

  /**
   * Returns the two prefixed siblings for {@code pattern}, in fixed order:
   *
   * <ol>
   *   <li>{@code /v2/physical-tenants/{physicalTenantId}<tail>} — existing API-client scheme.
   *   <li>{@code /physical-tenant/{physicalTenantId}/v2<tail>} — webapp-aligned scheme (D7).
   * </ol>
   *
   * Returns an empty list if {@code pattern} is not eligible (doesn't start with {@code /v2}, or
   * looks like {@code /v2foo}).
   */
  @VisibleForTesting
  List<String> prefixes(final String pattern) {
    if (pattern == null || !pattern.startsWith(V2)) {
      // Only /v2 routes participate in the physical-tenant addressing scheme.
      return List.of();
    }
    final String tail = pattern.substring(V2.length());
    if (!tail.isEmpty() && !tail.startsWith("/")) {
      // Avoid prefixing things like "/v2foo".
      return List.of();
    }
    return List.of(
        // existing API-client scheme: /v2/physical-tenants/{id}/<rest>
        V2 + "/physical-tenants/" + TENANT_VAR + tail,
        // webapp-aligned scheme: /physical-tenant/{id}/v2/<rest>
        "/physical-tenant/" + TENANT_VAR + V2 + tail);
  }

  private Class<?> resolveBeanType(final Object handler) {
    if (handler instanceof final String beanName) {
      return obtainApplicationContext().getType(beanName);
    }
    return handler != null ? handler.getClass() : null;
  }
}
