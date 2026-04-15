/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import static io.camunda.gateway.mapping.http.util.AdvancedSearchFilterUtil.mapToOperations;
import static java.util.Optional.ofNullable;

import io.camunda.gateway.mapping.http.converters.AuditLogActorTypeConverter;
import io.camunda.gateway.mapping.http.converters.AuditLogCategoryConverter;
import io.camunda.gateway.mapping.http.converters.AuditLogEntityTypeConverter;
import io.camunda.gateway.mapping.http.converters.AuditLogOperationTypeConverter;
import io.camunda.gateway.mapping.http.converters.AuditLogResultConverter;
import io.camunda.gateway.mapping.http.converters.BatchOperationTypeConverter;
import io.camunda.gateway.mapping.http.search.contract.generated.AuditLogFilterContract;
import io.camunda.search.filter.AuditLogFilter;
import io.camunda.search.filter.FilterBuilders;
import java.time.OffsetDateTime;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class AuditLogFilterMapper {

  private AuditLogFilterMapper() {}

  public static AuditLogFilter toAuditLogFilter(@Nullable final AuditLogFilterContract filter) {
    if (filter == null) {
      return FilterBuilders.auditLog().build();
    }
    final var builder = FilterBuilders.auditLog();
    ofNullable(filter.auditLogKey())
        .map(mapToOperations(String.class))
        .ifPresent(builder::auditLogKeyOperations);
    ofNullable(filter.processDefinitionKey())
        .map(mapToOperations(Long.class))
        .ifPresent(builder::processDefinitionKeyOperations);
    ofNullable(filter.processInstanceKey())
        .map(mapToOperations(Long.class))
        .ifPresent(builder::processInstanceKeyOperations);
    ofNullable(filter.elementInstanceKey())
        .map(mapToOperations(Long.class))
        .ifPresent(builder::elementInstanceKeyOperations);
    ofNullable(filter.operationType())
        .map(mapToOperations(String.class, new AuditLogOperationTypeConverter()))
        .ifPresent(builder::operationTypeOperations);
    ofNullable(filter.result())
        .map(mapToOperations(String.class, new AuditLogResultConverter()))
        .ifPresent(builder::resultOperations);
    ofNullable(filter.timestamp())
        .map(mapToOperations(OffsetDateTime.class))
        .ifPresent(builder::timestampOperations);
    ofNullable(filter.actorId())
        .map(mapToOperations(String.class))
        .ifPresent(builder::actorIdOperations);
    ofNullable(filter.actorType())
        .map(mapToOperations(String.class, new AuditLogActorTypeConverter()))
        .ifPresent(builder::actorTypeOperations);
    ofNullable(filter.agentElementId())
        .map(mapToOperations(String.class))
        .ifPresent(builder::agentElementIdOperations);
    ofNullable(filter.entityType())
        .map(mapToOperations(String.class, new AuditLogEntityTypeConverter()))
        .ifPresent(builder::entityTypeOperations);
    ofNullable(filter.entityKey())
        .map(mapToOperations(String.class))
        .ifPresent(builder::entityKeyOperations);
    ofNullable(filter.tenantId())
        .map(mapToOperations(String.class))
        .ifPresent(builder::tenantIdOperations);
    ofNullable(filter.category())
        .map(mapToOperations(String.class, new AuditLogCategoryConverter()))
        .ifPresent(builder::categoryOperations);
    ofNullable(filter.deploymentKey())
        .map(mapToOperations(Long.class))
        .ifPresent(builder::deploymentKeyOperations);
    ofNullable(filter.formKey())
        .map(mapToOperations(Long.class))
        .ifPresent(builder::formKeyOperations);
    ofNullable(filter.resourceKey())
        .map(mapToOperations(Long.class))
        .ifPresent(builder::resourceKeyOperations);
    ofNullable(filter.batchOperationType())
        .map(mapToOperations(String.class, new BatchOperationTypeConverter()))
        .ifPresent(builder::batchOperationTypeOperations);
    ofNullable(filter.processDefinitionId())
        .map(mapToOperations(String.class))
        .ifPresent(builder::processDefinitionIdOperations);
    ofNullable(filter.jobKey())
        .map(mapToOperations(Long.class))
        .ifPresent(builder::jobKeyOperations);
    ofNullable(filter.userTaskKey())
        .map(mapToOperations(Long.class))
        .ifPresent(builder::userTaskKeyOperations);
    ofNullable(filter.decisionRequirementsId())
        .map(mapToOperations(String.class))
        .ifPresent(builder::decisionRequirementsIdOperations);
    ofNullable(filter.decisionRequirementsKey())
        .map(mapToOperations(Long.class))
        .ifPresent(builder::decisionRequirementsKeyOperations);
    ofNullable(filter.decisionDefinitionId())
        .map(mapToOperations(String.class))
        .ifPresent(builder::decisionDefinitionIdOperations);
    ofNullable(filter.decisionDefinitionKey())
        .map(mapToOperations(Long.class))
        .ifPresent(builder::decisionDefinitionKeyOperations);
    ofNullable(filter.decisionEvaluationKey())
        .map(mapToOperations(Long.class))
        .ifPresent(builder::decisionEvaluationKeyOperations);
    ofNullable(filter.relatedEntityKey())
        .map(mapToOperations(String.class))
        .ifPresent(builder::relatedEntityKeyOperations);
    ofNullable(filter.relatedEntityType())
        .map(mapToOperations(String.class, new AuditLogEntityTypeConverter()))
        .ifPresent(builder::relatedEntityTypeOperations);
    ofNullable(filter.entityDescription())
        .map(mapToOperations(String.class))
        .ifPresent(builder::entityDescriptionOperations);
    return builder.build();
  }
}
