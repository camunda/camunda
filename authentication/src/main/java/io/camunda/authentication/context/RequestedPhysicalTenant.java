/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.context;

import io.camunda.authentication.filters.RequestedPhysicalTenantFilter;
import java.util.Optional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Read access to the physical tenant id resolved by {@link RequestedPhysicalTenantFilter} for the
 * current request. Returns {@link Optional#empty()} when called outside the PT URL scope or outside
 * a request-scoped thread.
 */
public final class RequestedPhysicalTenant {

  private RequestedPhysicalTenant() {}

  public static Optional<String> id() {
    final var attrs = RequestContextHolder.getRequestAttributes();
    if (attrs == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(
        (String)
            attrs.getAttribute(
                RequestedPhysicalTenantFilter.ATTRIBUTE, RequestAttributes.SCOPE_REQUEST));
  }
}
