/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import static io.camunda.gateway.mapping.http.util.AdvancedSearchFilterUtil.mapToOperations;

import io.camunda.gateway.mapping.http.converters.AuditLogActorTypeConverter;
import io.camunda.gateway.mapping.http.converters.AuditLogOperationTypeConverter;
import io.camunda.gateway.protocol.model.UserTaskAuditLogFilter;
import io.camunda.search.filter.AuditLogFilter;
import io.camunda.search.filter.FilterBuilders;
import java.time.OffsetDateTime;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class UserTaskAuditLogFilterMapper {

  private UserTaskAuditLogFilterMapper() {}

  public static AuditLogFilter toUserTaskAuditLogFilter(
      @Nullable final UserTaskAuditLogFilter filter) {
    if (filter == null) {
      return FilterBuilders.auditLog().build();
    }
    final var builder = FilterBuilders.auditLog();
    filter
        .getOperationType()
        .map(mapToOperations(String.class, new AuditLogOperationTypeConverter()))
        .ifPresent(builder::operationTypeOperations);
    filter.getResult().map(mapToOperations(String.class)).ifPresent(builder::resultOperations);
    filter
        .getTimestamp()
        .map(mapToOperations(OffsetDateTime.class))
        .ifPresent(builder::timestampOperations);
    filter.getActorId().map(mapToOperations(String.class)).ifPresent(builder::actorIdOperations);
    filter
        .getActorType()
        .map(mapToOperations(String.class, new AuditLogActorTypeConverter()))
        .ifPresent(builder::actorTypeOperations);
    return builder.build();
  }
}
