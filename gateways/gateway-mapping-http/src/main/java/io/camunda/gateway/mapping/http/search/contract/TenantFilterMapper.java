/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.TenantFilter;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class TenantFilterMapper {

  private TenantFilterMapper() {}

  public static TenantFilter toTenantFilter(
      final io.camunda.gateway.protocol.model.TenantFilter filter) {
    final var builder = FilterBuilders.tenant();
    Optional.ofNullable(filter.getTenantId()).ifPresent(builder::tenantId);
    Optional.ofNullable(filter.getName()).ifPresent(builder::name);
    return builder.build();
  }
}
