/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.rest;

import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.util.List;
import org.springframework.web.server.ServerWebExchange;

public final class TenantAttributeHolder {
  private static final String ATTRIBUTE_KEY = "io.camunda.zeebe.gateway.rest.tenantIds";

  private TenantAttributeHolder() {}

  public static List<String> tenantIds(final ServerWebExchange exchange) {
    return exchange.getAttributeOrDefault(
        ATTRIBUTE_KEY, List.of(TenantOwned.DEFAULT_TENANT_IDENTIFIER));
  }

  public static ServerWebExchange withTenantIds(
      final ServerWebExchange exchange, final List<String> tenantIds) {
    exchange.getAttributes().put(ATTRIBUTE_KEY, tenantIds);
    return exchange;
  }
}
