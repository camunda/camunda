/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.RoleFilter;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class RoleFilterMapper {

  private RoleFilterMapper() {}

  public static RoleFilter toRoleFilter(final io.camunda.gateway.protocol.model.RoleFilter filter) {
    final var builder = FilterBuilders.role();
    filter.getRoleId().ifPresent(builder::roleId);
    filter.getName().ifPresent(builder::name);
    return builder.build();
  }
}
