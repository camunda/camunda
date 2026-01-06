/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.write.domain.AuditLogDbModel;
import io.camunda.db.rdbms.write.domain.AuditLogDbModel.Builder;
import io.camunda.search.entities.AuditLogEntity;
import io.camunda.search.entities.AuditLogEntity.AuditLogActorType;
import io.camunda.search.entities.AuditLogEntity.AuditLogEntityType;
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationCategory;
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationResult;
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationType;
import io.camunda.search.entities.AuditLogEntity.AuditLogTenantScope;
import io.camunda.search.entities.BatchOperationType;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

public class AuditLogEntityMapperTest {

  @Test
  public void testToEntity() {
    // Given
    final AuditLogDbModel auditLogDbModel =
        new Builder()
            .auditLogKey("audit-key-test")
            .entityKey("1")
            .entityType(AuditLogEntityType.PROCESS_INSTANCE)
            .operationType(AuditLogOperationType.CREATE)
            .entityVersion(1)
            .entityValueType((short) 1)
            .entityOperationIntent((short) 2)
            .batchOperationKey(100L)
            .batchOperationType(BatchOperationType.MODIFY_PROCESS_INSTANCE)
            .timestamp(OffsetDateTime.now())
            .actorType(AuditLogActorType.USER)
            .actorId("actor-id")
            .tenantId("tenant-id")
            .tenantScope(AuditLogTenantScope.TENANT)
            .result(AuditLogOperationResult.SUCCESS)
            .annotation("test annotation")
            .category(AuditLogOperationCategory.DEPLOYED_RESOURCES)
            .processDefinitionId("process-id")
            .decisionRequirementsId("decision-requirements-id")
            .decisionDefinitionId("decision-id")
            .processDefinitionKey(200L)
            .processInstanceKey(300L)
            .elementInstanceKey(400L)
            .jobKey(500L)
            .userTaskKey(600L)
            .decisionRequirementsKey(700L)
            .decisionDefinitionKey(800L)
            .deploymentKey(900L)
            .formKey(1000L)
            .resourceKey(1100L)
            .build();

    // When
    final AuditLogEntity entity = AuditLogEntityMapper.toEntity(auditLogDbModel);

    // Then
    assertThat(entity).usingRecursiveComparison().isEqualTo(auditLogDbModel);
  }

  @Test
  public void testToEntityWithNullValues() {
    // Given
    final AuditLogDbModel auditLogDbModel =
        new Builder()
            .auditLogKey("audit-key-null-test")
            .entityKey("1")
            .entityType(AuditLogEntityType.USER_TASK)
            .operationType(AuditLogOperationType.UPDATE)
            .entityVersion(null)
            .entityValueType(null)
            .entityOperationIntent(null)
            .batchOperationKey(null)
            .batchOperationType(null)
            .timestamp(OffsetDateTime.now())
            .actorType(AuditLogActorType.CLIENT)
            .actorId(null)
            .tenantId(null)
            .tenantScope(null)
            .result(AuditLogOperationResult.FAIL)
            .annotation(null)
            .category(AuditLogOperationCategory.USER_TASKS)
            .processDefinitionId(null)
            .decisionRequirementsId(null)
            .decisionDefinitionId(null)
            .processDefinitionKey(null)
            .processInstanceKey(null)
            .elementInstanceKey(null)
            .jobKey(null)
            .userTaskKey(null)
            .decisionRequirementsKey(null)
            .decisionDefinitionKey(null)
            .decisionEvaluationKey(null)
            .deploymentKey(null)
            .formKey(null)
            .resourceKey(null)
            .build();

    // When
    final AuditLogEntity entity = AuditLogEntityMapper.toEntity(auditLogDbModel);

    // Then
    assertThat(entity.entityKey()).isNotNull();
    assertThat(entity.entityType()).isNotNull();
    assertThat(entity.operationType()).isNotNull();
    assertThat(entity.batchOperationKey()).isNull();
    assertThat(entity.batchOperationType()).isNull();
    assertThat(entity.timestamp()).isNotNull();
    assertThat(entity.actorType()).isNotNull();
    assertThat(entity.actorId()).isNull();
    assertThat(entity.tenantId()).isNull();
    assertThat(entity.result()).isNotNull();
    assertThat(entity.annotation()).isNull();
    assertThat(entity.category()).isNotNull();
    assertThat(entity.processDefinitionId()).isNull();
    assertThat(entity.decisionRequirementsId()).isNull();
    assertThat(entity.decisionDefinitionId()).isNull();
    assertThat(entity.processDefinitionKey()).isNull();
    assertThat(entity.processInstanceKey()).isNull();
    assertThat(entity.elementInstanceKey()).isNull();
    assertThat(entity.jobKey()).isNull();
    assertThat(entity.userTaskKey()).isNull();
    assertThat(entity.decisionRequirementsKey()).isNull();
    assertThat(entity.decisionDefinitionKey()).isNull();
    assertThat(entity.decisionEvaluationKey()).isNull();
  }
}
