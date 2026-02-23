/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.entities.AuditLogEntity.AuditLogActorType;
import io.camunda.search.entities.AuditLogEntity.AuditLogEntityType;
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationCategory;
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationResult;
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationType;
import io.camunda.search.entities.AuditLogEntity.AuditLogTenantScope;
import io.camunda.search.entities.BatchOperationType;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class AuditLogDbModelTest {

  @Test
  void shouldBuildAuditLogWithAllFields() {
    final AuditLogDbModel auditLog =
        new AuditLogDbModel.Builder()
            .auditLogKey("audit-key-123")
            .entityKey("1")
            .entityType(AuditLogEntityType.PROCESS_INSTANCE)
            .operationType(AuditLogOperationType.CREATE)
            .entityVersion(1)
            .entityValueType((short) 10)
            .entityOperationIntent((short) 20)
            .batchOperationKey(100L)
            .batchOperationType(BatchOperationType.CANCEL_PROCESS_INSTANCE)
            .timestamp(OffsetDateTime.now())
            .actorType(AuditLogActorType.USER)
            .actorId("user-123")
            .tenantId("tenant-1")
            .tenantScope(AuditLogTenantScope.TENANT)
            .result(AuditLogOperationResult.SUCCESS)
            .annotation("Manual cancellation")
            .category(AuditLogOperationCategory.DEPLOYED_RESOURCES)
            .processDefinitionId("process-1")
            .decisionDefinitionId("decision-1")
            .decisionRequirementsId("dmn-1")
            .processDefinitionKey(200L)
            .processInstanceKey(300L)
            .elementInstanceKey(400L)
            .jobKey(100L)
            .userTaskKey(150L)
            .decisionRequirementsKey(250L)
            .decisionDefinitionKey(200L)
            .deploymentKey(300L)
            .formKey(1000L)
            .resourceKey(1100L)
            .rootProcessInstanceKey(1200L)
            .build();

    assertThat(auditLog.auditLogKey()).isEqualTo("audit-key-123");
    assertThat(auditLog.entityKey()).isEqualTo("1");
    assertThat(auditLog.entityType()).isEqualTo(AuditLogEntityType.PROCESS_INSTANCE);
    assertThat(auditLog.operationType()).isEqualTo(AuditLogOperationType.CREATE);
    assertThat(auditLog.entityVersion()).isEqualTo(1);
    assertThat(auditLog.entityValueType()).isEqualTo((short) 10);
    assertThat(auditLog.entityOperationIntent()).isEqualTo((short) 20);
    assertThat(auditLog.batchOperationKey()).isEqualTo(100L);
    assertThat(auditLog.batchOperationType()).isEqualTo(BatchOperationType.CANCEL_PROCESS_INSTANCE);
    assertThat(auditLog.actorType()).isEqualTo(AuditLogActorType.USER);
    assertThat(auditLog.actorId()).isEqualTo("user-123");
    assertThat(auditLog.tenantId()).isEqualTo("tenant-1");
    assertThat(auditLog.tenantScope()).isEqualTo(AuditLogTenantScope.TENANT);
    assertThat(auditLog.result()).isEqualTo(AuditLogOperationResult.SUCCESS);
    assertThat(auditLog.annotation()).isEqualTo("Manual cancellation");
    assertThat(auditLog.category()).isEqualTo(AuditLogOperationCategory.DEPLOYED_RESOURCES);
    assertThat(auditLog.processDefinitionId()).isEqualTo("process-1");
    assertThat(auditLog.decisionRequirementsId()).isEqualTo("dmn-1");
    assertThat(auditLog.decisionDefinitionId()).isEqualTo("decision-1");
    assertThat(auditLog.processDefinitionKey()).isEqualTo(200L);
    assertThat(auditLog.processInstanceKey()).isEqualTo(300L);
    assertThat(auditLog.elementInstanceKey()).isEqualTo(400L);
    assertThat(auditLog.jobKey()).isEqualTo(100L);
    assertThat(auditLog.userTaskKey()).isEqualTo(150L);
    assertThat(auditLog.decisionRequirementsKey()).isEqualTo(250L);
    assertThat(auditLog.decisionDefinitionKey()).isEqualTo(200L);
    assertThat(auditLog.deploymentKey()).isEqualTo(300L);
    assertThat(auditLog.formKey()).isEqualTo(1000L);
    assertThat(auditLog.resourceKey()).isEqualTo(1100L);
    assertThat(auditLog.rootProcessInstanceKey()).isEqualTo(1200L);
  }

  @Test
  void shouldBuildAuditLogWithMinimalFields() {
    final AuditLogDbModel auditLog =
        new AuditLogDbModel.Builder()
            .auditLogKey("audit-key-minimal")
            .entityKey("1")
            .entityType(AuditLogEntityType.USER)
            .operationType(AuditLogOperationType.CREATE)
            .timestamp(OffsetDateTime.now())
            .actorType(AuditLogActorType.CLIENT)
            .result(AuditLogOperationResult.SUCCESS)
            .build();

    assertThat(auditLog.entityKey()).isEqualTo("1");
    assertThat(auditLog.entityType()).isEqualTo(AuditLogEntityType.USER);
    assertThat(auditLog.operationType()).isEqualTo(AuditLogOperationType.CREATE);
    assertThat(auditLog.actorType()).isEqualTo(AuditLogActorType.CLIENT);
    assertThat(auditLog.result()).isEqualTo(AuditLogOperationResult.SUCCESS);
    assertThat(auditLog.batchOperationKey()).isNull();
    assertThat(auditLog.processInstanceKey()).isNull();
  }

  @Test
  void shouldCopyAuditLogModel() {
    final AuditLogDbModel original =
        new AuditLogDbModel.Builder()
            .entityKey("1")
            .entityType(AuditLogEntityType.INCIDENT)
            .operationType(AuditLogOperationType.RESOLVE)
            .timestamp(OffsetDateTime.now())
            .actorType(AuditLogActorType.USER)
            .actorId("user-123")
            .result(AuditLogOperationResult.SUCCESS)
            .build();

    final AuditLogDbModel copied = original.copy(builder -> builder);

    assertThat(copied.entityKey()).isEqualTo(original.entityKey());
    assertThat(copied.entityType()).isEqualTo(original.entityType());
    assertThat(copied.operationType()).isEqualTo(original.operationType());
    assertThat(copied.actorType()).isEqualTo(original.actorType());
    assertThat(copied.actorId()).isEqualTo(original.actorId());
    assertThat(copied.result()).isEqualTo(original.result());
  }

  @Test
  void shouldHandleAllEntityTypes() {
    for (final AuditLogEntityType entityType : AuditLogEntityType.values()) {
      final AuditLogDbModel auditLog =
          new AuditLogDbModel.Builder()
              .entityKey("1")
              .entityType(entityType)
              .operationType(AuditLogOperationType.CREATE)
              .timestamp(OffsetDateTime.now())
              .actorType(AuditLogActorType.USER)
              .result(AuditLogOperationResult.SUCCESS)
              .build();

      assertThat(auditLog.entityType()).isEqualTo(entityType);
    }
  }

  @Test
  void shouldHandleAllOperationTypes() {
    for (final AuditLogOperationType operationType : AuditLogOperationType.values()) {
      final AuditLogDbModel auditLog =
          new AuditLogDbModel.Builder()
              .entityKey("1")
              .entityType(AuditLogEntityType.VARIABLE)
              .operationType(operationType)
              .timestamp(OffsetDateTime.now())
              .actorType(AuditLogActorType.USER)
              .result(AuditLogOperationResult.SUCCESS)
              .build();

      assertThat(auditLog.operationType()).isEqualTo(operationType);
    }
  }

  @Test
  void shouldHandleAllActorTypes() {
    for (final AuditLogActorType actorType : AuditLogActorType.values()) {
      final AuditLogDbModel auditLog =
          new AuditLogDbModel.Builder()
              .entityKey("1")
              .entityType(AuditLogEntityType.USER_TASK)
              .operationType(AuditLogOperationType.COMPLETE)
              .timestamp(OffsetDateTime.now())
              .actorType(actorType)
              .result(AuditLogOperationResult.SUCCESS)
              .build();

      assertThat(auditLog.actorType()).isEqualTo(actorType);
    }
  }

  @Test
  void shouldHandleAllOperationCategories() {
    for (final AuditLogOperationCategory category : AuditLogOperationCategory.values()) {
      final AuditLogDbModel auditLog =
          new AuditLogDbModel.Builder()
              .entityKey("1")
              .entityType(AuditLogEntityType.AUTHORIZATION)
              .operationType(AuditLogOperationType.CREATE)
              .timestamp(OffsetDateTime.now())
              .actorType(AuditLogActorType.USER)
              .result(AuditLogOperationResult.SUCCESS)
              .category(category)
              .build();

      assertThat(auditLog.category()).isEqualTo(category);
    }
  }

  @Test
  void shouldHandleAllBatchOperationTypes() {
    for (final BatchOperationType batchOperationType : BatchOperationType.values()) {
      final AuditLogDbModel auditLog =
          new AuditLogDbModel.Builder()
              .entityKey("1")
              .entityType(AuditLogEntityType.BATCH)
              .operationType(AuditLogOperationType.CREATE)
              .timestamp(OffsetDateTime.now())
              .actorType(AuditLogActorType.USER)
              .result(AuditLogOperationResult.SUCCESS)
              .batchOperationType(batchOperationType)
              .build();

      assertThat(auditLog.batchOperationType()).isEqualTo(batchOperationType);
    }
  }

  @Test
  void shouldHandleProcessInstanceRelatedFields() {
    final AuditLogDbModel auditLog =
        new AuditLogDbModel.Builder()
            .entityKey("1")
            .entityType(AuditLogEntityType.PROCESS_INSTANCE)
            .operationType(AuditLogOperationType.CANCEL)
            .timestamp(OffsetDateTime.now())
            .actorType(AuditLogActorType.USER)
            .result(AuditLogOperationResult.SUCCESS)
            .processDefinitionId("my-process")
            .processDefinitionKey(100L)
            .processInstanceKey(200L)
            .elementInstanceKey(300L)
            .build();

    assertThat(auditLog.processDefinitionId()).isEqualTo("my-process");
    assertThat(auditLog.processDefinitionKey()).isEqualTo(100L);
    assertThat(auditLog.processInstanceKey()).isEqualTo(200L);
    assertThat(auditLog.elementInstanceKey()).isEqualTo(300L);
  }

  @Test
  void shouldHandleDecisionRelatedFields() {
    final AuditLogDbModel auditLog =
        new AuditLogDbModel.Builder()
            .entityKey("1")
            .entityType(AuditLogEntityType.DECISION)
            .operationType(AuditLogOperationType.CREATE)
            .timestamp(OffsetDateTime.now())
            .actorType(AuditLogActorType.USER)
            .result(AuditLogOperationResult.SUCCESS)
            .decisionRequirementsId("dmn-1")
            .decisionDefinitionId("decision-1")
            .decisionRequirementsKey(100L)
            .decisionDefinitionKey(200L)
            .decisionEvaluationKey(300L)
            .build();

    assertThat(auditLog.decisionRequirementsId()).isEqualTo("dmn-1");
    assertThat(auditLog.decisionDefinitionId()).isEqualTo("decision-1");
    assertThat(auditLog.decisionRequirementsKey()).isEqualTo(100L);
    assertThat(auditLog.decisionDefinitionKey()).isEqualTo(200L);
    assertThat(auditLog.decisionEvaluationKey()).isEqualTo(300L);
  }

  @Test
  void shouldHandleUserTaskFields() {
    final AuditLogDbModel auditLog =
        new AuditLogDbModel.Builder()
            .entityKey("1")
            .entityType(AuditLogEntityType.USER_TASK)
            .operationType(AuditLogOperationType.ASSIGN)
            .timestamp(OffsetDateTime.now())
            .actorType(AuditLogActorType.USER)
            .result(AuditLogOperationResult.SUCCESS)
            .userTaskKey(500L)
            .build();

    assertThat(auditLog.userTaskKey()).isEqualTo(500L);
  }

  @Test
  void shouldHandleJobFields() {
    final AuditLogDbModel auditLog =
        new AuditLogDbModel.Builder()
            .entityKey("1")
            .entityType(AuditLogEntityType.INCIDENT)
            .operationType(AuditLogOperationType.RESOLVE)
            .timestamp(OffsetDateTime.now())
            .actorType(AuditLogActorType.USER)
            .result(AuditLogOperationResult.SUCCESS)
            .jobKey(400L)
            .build();

    assertThat(auditLog.jobKey()).isEqualTo(400L);
  }

  @Test
  void shouldHandleFailureWithDetails() {
    final AuditLogDbModel auditLog =
        new AuditLogDbModel.Builder()
            .entityKey("1")
            .entityType(AuditLogEntityType.PROCESS_INSTANCE)
            .operationType(AuditLogOperationType.CANCEL)
            .timestamp(OffsetDateTime.now())
            .actorType(AuditLogActorType.USER)
            .result(AuditLogOperationResult.FAIL)
            .build();

    assertThat(auditLog.result()).isEqualTo(AuditLogOperationResult.FAIL);
  }

  @Test
  void shouldHandleTenantScope() {
    final AuditLogDbModel auditLog =
        new AuditLogDbModel.Builder()
            .entityKey("1")
            .entityType(AuditLogEntityType.VARIABLE)
            .operationType(AuditLogOperationType.CANCEL)
            .timestamp(OffsetDateTime.now())
            .actorType(AuditLogActorType.USER)
            .result(AuditLogOperationResult.SUCCESS)
            .tenantId("tenant-1")
            .tenantScope(AuditLogTenantScope.GLOBAL)
            .build();

    assertThat(auditLog.tenantId()).isEqualTo("tenant-1");
    assertThat(auditLog.tenantScope()).isEqualTo(AuditLogTenantScope.GLOBAL);
  }

  @Test
  void shouldTruncateFields() {
    final AuditLogDbModel auditLog =
        new AuditLogDbModel.Builder().entityDescription("a".repeat(1000)).build();

    final AuditLogDbModel truncatedModel = auditLog.truncateEntityDescription(10, 100);
    assertThat(truncatedModel.entityDescription()).hasSize(10);
  }
}
