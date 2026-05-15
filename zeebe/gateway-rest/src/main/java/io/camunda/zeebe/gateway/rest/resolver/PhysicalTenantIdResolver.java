/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.resolver;

import io.camunda.zeebe.gateway.rest.annotation.PhysicalTenantId;
import io.camunda.zeebe.gateway.rest.context.PhysicalTenantContext;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Resolves controller method parameters annotated with {@link PhysicalTenantId} by reading the
 * physical tenant id from the current request via {@link PhysicalTenantContext}.
 *
 * <p>The interceptor that populates the request attribute runs before this resolver, so the value
 * is always present (defaulting to {@link PhysicalTenantContext#DEFAULT_PHYSICAL_TENANT_ID} when
 * the request did not carry the {@code /v2/physical-tenants/{physicalTenantId}/...} prefix).
 */
public class PhysicalTenantIdResolver implements HandlerMethodArgumentResolver {

  private static final Logger LOGGER = LoggerFactory.getLogger(PhysicalTenantIdResolver.class);

  @Override
  public boolean supportsParameter(final MethodParameter parameter) {
    return parameter.hasParameterAnnotation(PhysicalTenantId.class)
        && String.class.equals(parameter.getParameterType());
  }

  @Override
  public Object resolveArgument(
      final MethodParameter parameter,
      final ModelAndViewContainer mavContainer,
      final NativeWebRequest webRequest,
      final WebDataBinderFactory binderFactory) {
    final HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
    if (request == null) {
      LOGGER.debug(
          "Could not resolve physical tenant id for parameter '{}': no HTTP request bound to current thread",
          parameter);
      return PhysicalTenantContext.DEFAULT_PHYSICAL_TENANT_ID;
    }
    return PhysicalTenantContext.getPhysicalTenantId(request);
  }
}
