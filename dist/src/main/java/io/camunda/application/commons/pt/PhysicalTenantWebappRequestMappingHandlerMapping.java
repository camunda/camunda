/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.pt;

import io.camunda.zeebe.gateway.rest.context.PhysicalTenantContext;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Registers a {@code /physical-tenant/<id>/<webappBase>/...} sibling for every controller mapping
 * that lives under one of the recognised webapp base paths ({@code /operate}, {@code /tasklist},
 * {@code /admin}). The original (unprefixed) mappings stay registered on Spring's default {@link
 * RequestMappingHandlerMapping}; this handler registers ONLY the prefixed variants, so the same
 * controller methods answer both the unprefixed and the PT-prefixed URLs.
 *
 * <p>Why a second handler mapping rather than extending the existing {@code
 * PhysicalTenantRequestMappingHandlerMapping} (in {@code zeebe/gateway-rest}): that handler is the
 * primary {@link RequestMappingHandlerMapping}, wired via {@link
 * org.springframework.boot.webmvc.autoconfigure.WebMvcRegistrations}, and its scope is
 * intentionally limited to {@code @CamundaRestController} on {@code /v2/...} paths (the existing
 * REST PT addressing scheme). Operate/Tasklist controllers are plain Spring MVC
 * {@code @Controller}s. Keeping the webapp PT-prefixing in a separate handler mapping avoids
 * broadening the REST handler's contract and keeps the two mechanisms independently testable.
 *
 * <p>The prefixed sibling uses the same {@link
 * PhysicalTenantContext#PATH_VARIABLE_PHYSICAL_TENANT_ID} path variable name as the REST handler,
 * so the existing {@code PhysicalTenantInterceptor} resolves the tenant id from the path on every
 * prefixed webapp request without additional wiring.
 *
 * <p>Limitation: this handler only rewrites the URL the controller answers under. The controller's
 * own rendering (e.g. {@code OperateIndexController#tasklist} setting {@code contextPath} to {@code
 * /operate/}) is not PT-aware, so the rendered SPA still references unprefixed asset URLs once the
 * page is loaded. The PoC accepts this — see the PoC follow-up notes.
 */
public class PhysicalTenantWebappRequestMappingHandlerMapping extends RequestMappingHandlerMapping {

  private static final String TENANT_VAR =
      "{" + PhysicalTenantContext.PATH_VARIABLE_PHYSICAL_TENANT_ID + "}";

  private static final Set<String> WEBAPP_BASES = Set.of("/operate", "/tasklist", "/admin");

  @Override
  protected void registerHandlerMethod(
      final Object handler, final Method method, final RequestMappingInfo mapping) {
    // Register ONLY the PT-prefixed siblings. Spring's default RMHM keeps the originals; we don't
    // call super.registerHandlerMethod(handler, method, mapping) here to avoid double-registering
    // the unprefixed URLs (which would create an "ambiguous mapping" conflict with the default).
    final List<RequestMappingInfo> prefixed = withPhysicalTenantWebappPrefix(mapping);
    for (final RequestMappingInfo info : prefixed) {
      super.registerHandlerMethod(handler, method, info);
    }
  }

  private static List<RequestMappingInfo> withPhysicalTenantWebappPrefix(
      final RequestMappingInfo mapping) {
    final Set<String> patterns =
        mapping.getPathPatternsCondition() != null
            ? mapping.getPathPatternsCondition().getPatternValues()
            : Set.of();
    final Set<String> rewritten = new LinkedHashSet<>();
    for (final String pattern : patterns) {
      if (isWebappPath(pattern)) {
        rewritten.add("/physical-tenant/" + TENANT_VAR + pattern);
      }
    }
    if (rewritten.isEmpty()) {
      return List.of();
    }
    return List.of(mapping.mutate().paths(rewritten.toArray(String[]::new)).build());
  }

  private static boolean isWebappPath(final String pattern) {
    if (pattern == null) {
      return false;
    }
    for (final String base : WEBAPP_BASES) {
      if (pattern.equals(base) || pattern.startsWith(base + "/")) {
        return true;
      }
    }
    return false;
  }
}
