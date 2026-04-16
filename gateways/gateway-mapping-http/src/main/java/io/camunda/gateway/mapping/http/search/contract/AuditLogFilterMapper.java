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
import io.camunda.search.filter.AuditLogFilter;
import io.camunda.search.filter.FilterBuilders;
import java.time.OffsetDateTime;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class AuditLogFilterMapper {

  private AuditLogFilterMapper() {}

  public static AuditLogFilter toAuditLogFilter(
      final io.camunda.gateway.protocol.model.AuditLogFilter filter) {
    final var builder = FilterBuilders.auditLog();
    ofNullable(filter.getAuditLogKey())
        .map(mapToOperations(String.class))
        .ifPresent(builder::auditLogKeyOperations);
    ofNullable(filter.getProcessDefinitionKey())
        .map(mapToOperations(Long.class))
        .ifPresent(builder::processDefinitionKeyOperations);
    ofNullable(filter.getProcessInstanceKey())
        .map(mapToOperations(Long.class))
        .ifPresent(builder::processInstanceKeyOperations);
    ofNullable(filter.getElementInstanceKey())
        .map(mapToOperations(Long.class))
        .ifPresent(builder::elementInstanceKeyOperations);
    ofNullable(filter.getOperationType())
        .map(mapToOperations(String.class, new AuditLogOperationTypeConverter()))
        .ifPresent(builder::operationTypeOperations);
    ofNullable(filter.getResult())
        .map(mapToOperations(String.class, new AuditLogResultConverter()))
        .ifPresent(builder::resultOperations);
    ofNullable(filter.getTimestamp())
        .map(mapToOperations(OffsetDateTime.class))
        .ifPresent(builder::timestampOperations);
    ofNullable(filter.getActorId())
        .map(mapToOperations(String.class))
        .ifPresent(builder::actorIdOperations);
    ofNullable(filter.getActorType())
        .map(mapToOperations(String.class, new AuditLogActorTypeConverter()))
        .ifPresent(builder::actorTypeOperations);
    ofNullable(filter.getAgentElementId())
        .map(mapToOperations(String.class))
        .ifPresent(builder::agentElementIdOperations);
    ofNullable(filter.getEntityType())
        .map(mapToOperations(String.class, new AuditLogEntityTypeConverter()))
        .ifPresent(builder::entityTypeOperations);
    ofNullable(filter.getEntityKey())
        .map(mapToOperations(String.class))
        .ifPresent(builder::entityKeyOperations);
    ofNullable(filter.getTenantId())
        .map(mapToOperations(String.class))
        .ifPresent(builder::tenantIdOperations);
    ofNullable(filter.getCategory())
        .map(mapToOperations(String.class, new AuditLogCategoryConverter()))
        .ifPresent(builder::categoryOperations);
    ofNullable(filter.getDeploymentKey())
        .map(mapToOperations(Long.class))
        .ifPresent(builder::deploymentKeyOperations);
    ofNullable(filter.getFormKey())
        .map(mapToOperations(Long.class))
        .ifPresent(builder::formKeyOperations);
    ofNullable(filter.getResourceKey())
        .map(mapToOperations(Long.class))
        .ifPresent(builder::resourceKeyOperations);
    ofNullable(filter.getBatchOperationType())
        .map(mapToOperations(String.class, new BatchOperationTypeConverter()))
        .ifPresent(builder::batchOperationTypeOperations);
    ofNullable(filter.getProcessDefinitionId())
        .map(mapToOperations(String.class))
        .ifPresent(builder::processDefinitionIdOperations);
    ofNullable(filter.getJobKey())
        .map(mapToOperations(Long.class))
        .ifPresent(builder::jobKeyOperations);
    ofNullable(filter.getUserTaskKey())
        .map(mapToOperations(Long.class))
        .ifPresent(builder::userTaskKeyOperations);
    ofNullable(filter.getDecisionRequirementsId())
        .map(mapToOperations(String.class))
        .ifPresent(builder::decisionRequirementsIdOperations);
    ofNullable(filter.getDecisionRequirementsKey())
        .map(mapToOperations(Long.class))
        .ifPresent(builder::decisionRequirementsKeyOperations);
    ofNullable(filter.getDecisionDefinitionId())
        .map(mapToOperations(String.class))
        .ifPresent(builder::decisionDefinitionIdOperations);
    ofNullable(filter.getDecisionDefinitionKey())
        .map(mapToOperations(Long.class))
        .ifPresent(builder::decisionDefinitionKeyOperations);
    ofNullable(filter.getDecisionEvaluationKey())
        .map(mapToOperations(Long.class))
        .ifPresent(builder::decisionEvaluationKeyOperations);
    ofNullable(filter.getRelatedEntityKey())
        .map(mapToOperations(String.class))
        .ifPresent(builder::relatedEntityKeyOperations);
    ofNullable(filter.getRelatedEntityType())
        .map(mapToOperations(String.class, new AuditLogEntityTypeConverter()))
        .ifPresent(builder::relatedEntityTypeOperations);
    ofNullable(filter.getEntityDescription())
        .map(mapToOperations(String.class))
        .ifPresent(builder::entityDescriptionOperations);
    return builder.build();
  }
}
