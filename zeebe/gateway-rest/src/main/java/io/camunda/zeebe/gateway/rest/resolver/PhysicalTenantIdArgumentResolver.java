/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.resolver;

import io.camunda.spring.utils.PhysicalTenantContext;
import io.camunda.zeebe.gateway.rest.annotation.PhysicalTenantId;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Resolves controller method parameters annotated with {@link PhysicalTenantId} from {@link
 * PhysicalTenantContext#current()}.
 *
 * <p>{@code PhysicalTenantFilter} stamps the id for tenant-prefixed paths ({@code
 * /physical-tenants/{physicalTenantId}/v2/...}); {@code current()} returns it, or falls back to
 * {@link PhysicalTenantContext#DEFAULT_PHYSICAL_TENANT_ID} for cluster (non-prefixed) paths — so
 * the resolved value is never {@code null}.
 */
public class PhysicalTenantIdArgumentResolver implements HandlerMethodArgumentResolver {

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
    return PhysicalTenantContext.current();
  }
}
