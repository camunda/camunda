/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest;

import static io.camunda.zeebe.protocol.record.value.TenantOwned.DEFAULT_TENANT_IDENTIFIER;
import static org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

public final class TenantAttributeHolder {
  private static final String ATTRIBUTE_KEY = "io.camunda.zeebe.gateway.rest.tenantIds";

  private TenantAttributeHolder() {}

  public static Set<String> tenantIds() {
    final var requestAttributes = getCurrentRequestAttributes();
    return getTenantsOrDefaultTenant(requestAttributes);
  }

  public static void withTenantIds(final List<String> tenantIds) {
    final var requestAttributes = getCurrentRequestAttributes();
    final var tenantsOptional =
        Optional.ofNullable(tenantIds == null ? null : new HashSet<>(tenantIds));
    requestAttributes.setAttribute(ATTRIBUTE_KEY, tenantsOptional, SCOPE_REQUEST);
  }

  private static RequestAttributes getCurrentRequestAttributes() {
    return RequestContextHolder.currentRequestAttributes();
  }

  private static Set<String> getTenantsOrDefaultTenant(final RequestAttributes requestAttributes) {
    final var tenants = requestAttributes.getAttribute(ATTRIBUTE_KEY, SCOPE_REQUEST);

    if (tenants != null) {
      return ((Optional<Set<String>>) tenants).orElse(null);
    } else {
      return Set.of(DEFAULT_TENANT_IDENTIFIER);
    }
  }
}
