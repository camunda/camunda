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
            -1L,
            null);

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
            -1L,
            null);

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
            OffsetDateTime.now(),
            null,
            null);

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
            OffsetDateTime.now(),
            null,
            null);

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
    // distinct keys so the batch-size limit (not duplicate-key absorption) is what blocks the merge
    final var merger = new InsertVariableMerger(newVariable(3L), 2); // Max batch size of 2

    final var parameter =
        new BatchInsertDto<>(List.of(newVariable(1L), newVariable(2L))); // Already at max size

    final var queueItem =
        new QueueItem(ContextType.VARIABLE, WriteStatementType.INSERT, 1L, "statement", parameter);

    assertThat(merger.canBeMerged(queueItem)).isFalse();
  }

  @Test
  void shouldNotMergeWhenMaxBatchSizeIsOne() {
    // distinct key so the maxBatchSize==1 short-cut (not duplicate-key absorption) blocks the merge
    final var merger = new InsertVariableMerger(newVariable(2L), 1); // Max batch size of 1

    final var parameter = new BatchInsertDto<>(List.of(newVariable(1L)));

    final var queueItem =
        new QueueItem(ContextType.VARIABLE, WriteStatementType.INSERT, 1L, "statement", parameter);

    // Should return false immediately due to maxBatchSize == 1 short-cut
    assertThat(merger.canBeMerged(queueItem)).isFalse();
  }

  @Test
  void shouldDropDuplicateWhenKeyAlreadyPresentInBatch() {
    final var original = newVariable(1L);
    // a different model instance carrying the SAME key, mirroring a re-enqueued record on retry
    final var duplicate = newVariable(1L);

    final var merger = new InsertVariableMerger(duplicate, 50);
    final var parameter = new BatchInsertDto<>(List.of(original));
    final var queueItem =
        new QueueItem(ContextType.VARIABLE, WriteStatementType.INSERT, 1L, "statement", parameter);

    // the merger claims the item so the writer does NOT enqueue a second batch item ...
    assertThat(merger.canBeMerged(queueItem)).isTrue();

    // ... and merge() is a no-op: the key stays in the batch exactly once
    final var mergedItem = merger.merge(queueItem);
    assertThat(mergedItem.parameter())
        .asInstanceOf(InstanceOfAssertFactories.type(BatchInsertDto.class))
        .satisfies(
            p -> {
              assertThat(p.dbModels()).hasSize(1);
              assertThat(p.dbModels().get(0)).isEqualTo(original);
            });
  }

  @Test
  void shouldClaimAndDropDuplicateEvenWhenBatchIsFull() {
    final var duplicateOfFirst = newVariable(1L);
    final var merger = new InsertVariableMerger(duplicateOfFirst, 2); // batch is at max size

    final var parameter = new BatchInsertDto<>(List.of(newVariable(1L), newVariable(2L)));
    final var queueItem =
        new QueueItem(ContextType.VARIABLE, WriteStatementType.INSERT, 1L, "statement", parameter);

    // even though the batch is full, a present key is claimed so it is not spilled into a new batch
    assertThat(merger.canBeMerged(queueItem)).isTrue();

    final var mergedItem = merger.merge(queueItem);
    assertThat(mergedItem.parameter())
        .asInstanceOf(InstanceOfAssertFactories.type(BatchInsertDto.class))
        .satisfies(p -> assertThat(p.dbModels()).hasSize(2));
  }

  private static VariableDbModel newVariable(final long key) {
    return new VariableDbModel(
        key,
        "var" + key,
        ValueTypeEnum.STRING,
        null,
        null,
        "value" + key,
        null,
        false,
        100L,
        200L,
        200L,
        "process1",
        "tenant1",
        1,
        -1L,
        null);
  }
}
