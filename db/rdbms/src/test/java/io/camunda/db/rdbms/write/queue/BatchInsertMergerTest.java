/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.camunda.db.rdbms.write.domain.AuditLogDbModel;
import io.camunda.db.rdbms.write.domain.VariableDbModel;
import io.camunda.search.entities.AuditLogEntity.AuditLogActorType;
import io.camunda.search.entities.AuditLogEntity.AuditLogEntityType;
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationCategory;
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationResult;
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationType;
import io.camunda.search.entities.AuditLogEntity.AuditLogTenantScope;
import io.camunda.search.entities.ValueTypeEnum;
import java.time.OffsetDateTime;
import java.util.List;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;

class BatchInsertMergerTest {

  @Test
  void shouldNotMergeWhenMaxBatchSizeEqualsOne() {
    final var variable = mock(VariableDbModel.class);

    final var merger = new InsertVariableMerger(variable, 1);
    final var queueItem = mock(QueueItem.class);

    assertThat(merger.canBeMerged(queueItem)).isFalse();
  }

  @Test
  void shouldMergeVariablesUsingGenericMerger() {
    final var variable1 =
        new VariableDbModel(
            1L,
            "var1",
            ValueTypeEnum.STRING,
            null,
            null,
            "value1",
            null,
            false,
            100L,
            200L,
            200L,
            "process1",
            "tenant1",
            1,
            -1L);

    final var variable2 =
        new VariableDbModel(
            2L,
            "var2",
            ValueTypeEnum.STRING,
            null,
            null,
            "value2",
            null,
            false,
            101L,
            201L,
            201L,
            "process1",
            "tenant1",
            1,
            -1L);

    final var merger = new InsertVariableMerger(variable2, 50);
    final var parameter = new BatchInsertDto<>(List.of(variable1));
    final var queueItem =
        new QueueItem(ContextType.VARIABLE, WriteStatementType.INSERT, 1L, "statement", parameter);

    assertThat(merger.canBeMerged(queueItem)).isTrue();

    final var mergedItem = merger.merge(queueItem);
    assertThat(mergedItem.parameter())
        .asInstanceOf(InstanceOfAssertFactories.type(BatchInsertDto.class))
        .satisfies(
            p -> {
              assertThat(p.dbModels()).hasSize(2);
              assertThat(p.dbModels().get(0)).isEqualTo(variable1);
              assertThat(p.dbModels().get(1)).isEqualTo(variable2);
            });
  }

  @Test
  void shouldMergeAuditLogsUsingGenericMerger() {
    final var auditLog1 =
        new AuditLogDbModel(
            "key1",
            "entity1",
            AuditLogEntityType.PROCESS_INSTANCE,
            AuditLogOperationType.CREATE,
            1,
            null,
            null,
            null,
            null,
            OffsetDateTime.now(),
            AuditLogActorType.USER,
            "user1",
            null,
            "tenant1",
            AuditLogTenantScope.TENANT,
            AuditLogOperationResult.SUCCESS,
            null,
            AuditLogOperationCategory.ADMIN,
            "process1",
            null,
            null,
            100L,
            200L,
            200L,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            1,
            OffsetDateTime.now());

    final var auditLog2 =
        new AuditLogDbModel(
            "key2",
            "entity2",
            AuditLogEntityType.PROCESS_INSTANCE,
            AuditLogOperationType.UPDATE,
            1,
            null,
            null,
            null,
            null,
            OffsetDateTime.now(),
            AuditLogActorType.USER,
            "user1",
            null,
            "tenant1",
            AuditLogTenantScope.TENANT,
            AuditLogOperationResult.SUCCESS,
            null,
            AuditLogOperationCategory.ADMIN,
            "process1",
            null,
            null,
            100L,
            201L,
            201L,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            1,
            OffsetDateTime.now());

    final var merger = new InsertAuditLogMerger(auditLog2, 50);
    final var parameter = new BatchInsertDto<>(List.of(auditLog1));
    final var queueItem =
        new QueueItem(
            ContextType.AUDIT_LOG, WriteStatementType.INSERT, "key1", "statement", parameter);

    assertThat(merger.canBeMerged(queueItem)).isTrue();

    final var mergedItem = merger.merge(queueItem);
    assertThat(mergedItem.parameter())
        .asInstanceOf(InstanceOfAssertFactories.type(BatchInsertDto.class))
        .satisfies(
            p -> {
              assertThat(p.dbModels()).hasSize(2);
              assertThat(p.dbModels().get(0)).isEqualTo(auditLog1);
              assertThat(p.dbModels().get(1)).isEqualTo(auditLog2);
            });
  }

  @Test
  void shouldNotMergeWhenBatchSizeLimitReached() {
    final var variable =
        new VariableDbModel(
            1L,
            "var",
            ValueTypeEnum.STRING,
            null,
            null,
            "value",
            null,
            false,
            100L,
            200L,
            200L,
            "process1",
            "tenant1",
            1,
            -1L);

    final var merger = new InsertVariableMerger(variable, 2); // Max batch size of 2

    final var parameter = new BatchInsertDto<>(List.of(variable, variable)); // Already at max size

    final var queueItem =
        new QueueItem(ContextType.VARIABLE, WriteStatementType.INSERT, 1L, "statement", parameter);

    assertThat(merger.canBeMerged(queueItem)).isFalse();
  }

  @Test
  void shouldNotMergeWhenMaxBatchSizeIsOne() {
    final var variable =
        new VariableDbModel(
            1L,
            "var",
            ValueTypeEnum.STRING,
            null,
            null,
            "value",
            null,
            false,
            100L,
            200L,
            200L,
            "process1",
            "tenant1",
            1,
            -1L);

    final var merger = new InsertVariableMerger(variable, 1); // Max batch size of 1

    final var parameter = new BatchInsertDto<>(List.of(variable));

    final var queueItem =
        new QueueItem(ContextType.VARIABLE, WriteStatementType.INSERT, 1L, "statement", parameter);

    // Should return false immediately due to maxBatchSize == 1 short-cut
    assertThat(merger.canBeMerged(queueItem)).isFalse();
  }
}
