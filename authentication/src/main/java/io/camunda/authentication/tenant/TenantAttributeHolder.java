/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.tenant;

import static org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Provides static typed access to a thread-local request attribute that holds the list of
 * authenticated tenant ids for the request.
 */
public final class TenantAttributeHolder {
  private static final String ATTRIBUTE_KEY = "io.camunda.zeebe.gateway.rest.tenantIds";

  private TenantAttributeHolder() {}

  public static List<String> getTenantIds() {
    final Object tenantAttributeValue =
        RequestContextHolder.currentRequestAttributes().getAttribute(ATTRIBUTE_KEY, SCOPE_REQUEST);
    return (List<String>) tenantAttributeValue;
  }

  public static void setTenantIds(@Nonnull final Collection<String> tenantIds) {
    Objects.requireNonNull(tenantIds, "tenantIds must not be null");
    final var requestAttributes = RequestContextHolder.currentRequestAttributes();
    requestAttributes.setAttribute(ATTRIBUTE_KEY, tenantIds.stream().toList(), SCOPE_REQUEST);
  }
}
