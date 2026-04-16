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
    filter
        .getAuditLogKey()
        .map(mapToOperations(String.class))
        .ifPresent(builder::auditLogKeyOperations);
    filter
        .getProcessDefinitionKey()
        .map(mapToOperations(Long.class))
        .ifPresent(builder::processDefinitionKeyOperations);
    filter
        .getProcessInstanceKey()
        .map(mapToOperations(Long.class))
        .ifPresent(builder::processInstanceKeyOperations);
    filter
        .getElementInstanceKey()
        .map(mapToOperations(Long.class))
        .ifPresent(builder::elementInstanceKeyOperations);
    filter
        .getOperationType()
        .map(mapToOperations(String.class, new AuditLogOperationTypeConverter()))
        .ifPresent(builder::operationTypeOperations);
    filter
        .getResult()
        .map(mapToOperations(String.class, new AuditLogResultConverter()))
        .ifPresent(builder::resultOperations);
    filter
        .getTimestamp()
        .map(mapToOperations(OffsetDateTime.class))
        .ifPresent(builder::timestampOperations);
    filter.getActorId().map(mapToOperations(String.class)).ifPresent(builder::actorIdOperations);
    filter
        .getActorType()
        .map(mapToOperations(String.class, new AuditLogActorTypeConverter()))
        .ifPresent(builder::actorTypeOperations);
    filter
        .getAgentElementId()
        .map(mapToOperations(String.class))
        .ifPresent(builder::agentElementIdOperations);
    filter
        .getEntityType()
        .map(mapToOperations(String.class, new AuditLogEntityTypeConverter()))
        .ifPresent(builder::entityTypeOperations);
    filter
        .getEntityKey()
        .map(mapToOperations(String.class))
        .ifPresent(builder::entityKeyOperations);
    filter.getTenantId().map(mapToOperations(String.class)).ifPresent(builder::tenantIdOperations);
    filter
        .getCategory()
        .map(mapToOperations(String.class, new AuditLogCategoryConverter()))
        .ifPresent(builder::categoryOperations);
    filter
        .getDeploymentKey()
        .map(mapToOperations(Long.class))
        .ifPresent(builder::deploymentKeyOperations);
    filter.getFormKey().map(mapToOperations(Long.class)).ifPresent(builder::formKeyOperations);
    filter
        .getResourceKey()
        .map(mapToOperations(Long.class))
        .ifPresent(builder::resourceKeyOperations);
    filter
        .getBatchOperationType()
        .map(mapToOperations(String.class, new BatchOperationTypeConverter()))
        .ifPresent(builder::batchOperationTypeOperations);
    filter
        .getProcessDefinitionId()
        .map(mapToOperations(String.class))
        .ifPresent(builder::processDefinitionIdOperations);
    filter.getJobKey().map(mapToOperations(Long.class)).ifPresent(builder::jobKeyOperations);
    filter
        .getUserTaskKey()
        .map(mapToOperations(Long.class))
        .ifPresent(builder::userTaskKeyOperations);
    filter
        .getDecisionRequirementsId()
        .map(mapToOperations(String.class))
        .ifPresent(builder::decisionRequirementsIdOperations);
    filter
        .getDecisionRequirementsKey()
        .map(mapToOperations(Long.class))
        .ifPresent(builder::decisionRequirementsKeyOperations);
    filter
        .getDecisionDefinitionId()
        .map(mapToOperations(String.class))
        .ifPresent(builder::decisionDefinitionIdOperations);
    filter
        .getDecisionDefinitionKey()
        .map(mapToOperations(Long.class))
        .ifPresent(builder::decisionDefinitionKeyOperations);
    filter
        .getDecisionEvaluationKey()
        .map(mapToOperations(Long.class))
        .ifPresent(builder::decisionEvaluationKeyOperations);
    filter
        .getRelatedEntityKey()
        .map(mapToOperations(String.class))
        .ifPresent(builder::relatedEntityKeyOperations);
    filter
        .getRelatedEntityType()
        .map(mapToOperations(String.class, new AuditLogEntityTypeConverter()))
        .ifPresent(builder::relatedEntityTypeOperations);
    filter
        .getEntityDescription()
        .map(mapToOperations(String.class))
        .ifPresent(builder::entityDescriptionOperations);
    return builder.build();
  }
}
