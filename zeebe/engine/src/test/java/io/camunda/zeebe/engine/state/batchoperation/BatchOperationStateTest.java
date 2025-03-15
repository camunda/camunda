/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.batchoperation;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperation.BatchOperationState;
import io.camunda.zeebe.engine.state.mutable.MutableBatchOperationState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationExecutionRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationSubbatchRecord;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public class BatchOperationStateTest {

  private static final int MAX_CHUNK_SIZE = 15;

  private MutableProcessingState processingState;
  private MutableBatchOperationState state;

  @BeforeEach
  public void setup() {
    state = processingState.getBatchOperationState();
  }

  @Test
  void shouldCreateBatchOperation() {
    // given
    final var batchOperationKey = 1L;
    final var type = BatchOperationType.PROCESS_CANCELLATION;
    final var filter =
        new ProcessInstanceFilter.Builder()
            .processDefinitionIds("process")
            .processDefinitionVersions(1)
            .build();
    final var roleRecord =
        new BatchOperationCreationRecord()
            .setBatchOperationKey(batchOperationKey)
            .setBatchOperationType(type)
            .setFilter(filter);

    // when
    state.create(batchOperationKey, roleRecord);

    // then
    final var batchOperation = state.get(batchOperationKey).get();
    assertThat(batchOperation.getKey()).isEqualTo(batchOperationKey);
    assertThat(batchOperation.getBatchOperationType()).isEqualTo(type);
    assertThat(batchOperation.getFilter(ProcessInstanceFilter.class)).isEqualTo(filter);
    assertThat(batchOperation.getStatus()).isEqualTo(BatchOperationState.ACTIVATED);
  }

  @Test
  void newBatchShouldBePending() {
    // given

    // when
    final var batchOperationKey = 1L;
    final var roleRecord =
        new BatchOperationCreationRecord()
            .setBatchOperationKey(batchOperationKey)
            .setBatchOperationType(BatchOperationType.PROCESS_CANCELLATION);
    state.create(batchOperationKey, roleRecord);

    // then
    final var pendingKeys = new ArrayList<>();
    state.foreachPendingBatchOperation(bo -> pendingKeys.add(bo.getKey()));

    assertThat(pendingKeys).containsExactly(batchOperationKey);
  }

  @Test
  void startedBatchShouldNotBePending() {
    // given
    final var batchOperationKey = 1L;
    final var roleRecord =
        new BatchOperationCreationRecord()
            .setBatchOperationKey(batchOperationKey)
            .setBatchOperationType(BatchOperationType.PROCESS_CANCELLATION);
    state.create(batchOperationKey, roleRecord);

    // when
    state.removeFromPending(
        batchOperationKey,
        new BatchOperationExecutionRecord().setBatchOperationKey(batchOperationKey));

    // then
    final var pendingKeys = new ArrayList<>();
    state.foreachPendingBatchOperation(bo -> pendingKeys.add(bo.getKey()));

    assertThat(pendingKeys).isEmpty();
  }

  @Test
  void shouldDeleteOnCancel() {
    // given
    final var batchOperationKey = createDefaultBatch(1, 10);

    // when
    state.cancel(
        batchOperationKey,
        new BatchOperationExecutionRecord().setBatchOperationKey(batchOperationKey));

    // then
    assertThat(state.get(batchOperationKey)).isEmpty();
  }

  @Test
  void shouldDeleteOnComplete() {
    // given
    final var batchOperationKey = createDefaultBatch(1, 10);

    // when
    state.complete(
        batchOperationKey,
        new BatchOperationExecutionRecord().setBatchOperationKey(batchOperationKey));

    // then
    assertThat(state.get(batchOperationKey)).isEmpty();
  }

  @Test
  void shouldBeAbleToPause() {
    // given
    final var batchOperationKey = createDefaultBatch(1, 10);

    // then
    final var batchOperation = state.get(batchOperationKey).get();
    assertThat(batchOperation.canPause()).isTrue();
  }

  @Test
  void shouldNotBeAbleToResumeWhenNotPaused() {
    // given
    final var batchOperationKey = createDefaultBatch(1, 10);

    // then
    final var batchOperation = state.get(batchOperationKey).get();
    assertThat(batchOperation.canResume()).isFalse();
  }

  @Test
  void shouldBePaused() {
    // given
    final var batchOperationKey = createDefaultBatch(1, 10);

    // when
    state.pause(
        batchOperationKey,
        new BatchOperationExecutionRecord().setBatchOperationKey(batchOperationKey));

    // then
    final var batchOperation = state.get(batchOperationKey).get();
    assertThat(batchOperation.getStatus()).isEqualTo(BatchOperationState.PAUSED);
    assertThat(batchOperation.canResume()).isTrue();
  }

  @Test
  void shouldBeActiveAfterResumed() {
    // given
    final var batchOperationKey = createDefaultBatch(1, 10);
    state.pause(
        batchOperationKey,
        new BatchOperationExecutionRecord().setBatchOperationKey(batchOperationKey));

    // when
    state.resume(
        batchOperationKey,
        new BatchOperationExecutionRecord().setBatchOperationKey(batchOperationKey));

    // then
    final var batchOperation = state.get(batchOperationKey).get();
    assertThat(batchOperation.getStatus()).isEqualTo(BatchOperationState.ACTIVATED);
  }

  @Test
  void shouldReturnNoKeysIfEmpty() {
    // given
    final var batchOperationKey = createDefaultBatch(1, 0);

    // when
    final var keys = state.getNextEntityKeys(batchOperationKey, 5);

    // then
    assertThat(keys).isEmpty();
  }

  @Test
  void shouldAddAndGetKeys() {
    // given
    final var batchOperationKey = 1L;
    final var createRecord =
        new BatchOperationCreationRecord()
            .setBatchOperationKey(batchOperationKey)
            .setBatchOperationType(BatchOperationType.PROCESS_CANCELLATION)
            .setFilter(new ProcessInstanceFilter.Builder().build());
    state.create(batchOperationKey, createRecord);

    // when
    final var subbatchKey = 2L;
    final var keys = LongStream.rangeClosed(0, 10).boxed().toList();
    final var subbatchRecord =
        new BatchOperationSubbatchRecord()
            .setBatchOperationKey(batchOperationKey)
            .setSubbatchKey(subbatchKey)
            .setKeys(keys);
    state.appendKeys(batchOperationKey, subbatchRecord);

    // then
    final var persistedBatchOperation = state.get(batchOperationKey).get();

    assertThat(persistedBatchOperation.getMinChunkKey()).isEqualTo(0);
    assertThat(persistedBatchOperation.getMaxChunkKey()).isEqualTo(0);

    final var nextKeys = state.getNextEntityKeys(batchOperationKey, 5);
    assertThat(nextKeys).hasSize(5);
    assertThat(nextKeys).containsSequence(List.of(0L, 1L, 2L, 3L, 4L));
  }

  @Test
  void shouldOnlyGetExistingNextKeys() {
    // given
    final var batchOperationKey = 1L;
    final var createRecord =
        new BatchOperationCreationRecord()
            .setBatchOperationKey(batchOperationKey)
            .setBatchOperationType(BatchOperationType.PROCESS_CANCELLATION)
            .setFilter(new ProcessInstanceFilter.Builder().build());
    state.create(batchOperationKey, createRecord);

    // when
    final var subbatchKey = 2L;
    final var keys = LongStream.rangeClosed(0, 4).boxed().toList();
    final var subbatchRecord =
        new BatchOperationSubbatchRecord()
            .setBatchOperationKey(batchOperationKey)
            .setSubbatchKey(subbatchKey)
            .setKeys(keys);
    state.appendKeys(batchOperationKey, subbatchRecord);

    // then
    final var persistedBatchOperation = state.get(batchOperationKey).get();

    assertThat(persistedBatchOperation.getMinChunkKey()).isEqualTo(0);
    assertThat(persistedBatchOperation.getMaxChunkKey()).isEqualTo(0);

    final var nextKeys = state.getNextEntityKeys(batchOperationKey, 10);
    assertThat(nextKeys).hasSize(5);
    assertThat(nextKeys).containsSequence(List.of(0L, 1L, 2L, 3L, 4L));
  }

  @Test
  void shouldAddManyKeys() {
    // given
    final var batchOperationKey = 1L;
    final var createRecord =
        new BatchOperationCreationRecord()
            .setBatchOperationKey(batchOperationKey)
            .setBatchOperationType(BatchOperationType.PROCESS_CANCELLATION)
            .setFilter(new ProcessInstanceFilter.Builder().build());
    state.create(batchOperationKey, createRecord);

    // when
    final var subbatchKey = 2L;
    final var keys = LongStream.range(0, 99).boxed().toList();
    final var subbatchRecord =
        new BatchOperationSubbatchRecord()
            .setBatchOperationKey(batchOperationKey)
            .setSubbatchKey(subbatchKey)
            .setKeys(keys);
    state.appendKeys(batchOperationKey, subbatchRecord);

    // then
    final var persistedBatchOperation = state.get(batchOperationKey).get();

    assertThat(persistedBatchOperation.getMinChunkKey()).isEqualTo(0);
    assertThat(persistedBatchOperation.getMaxChunkKey()).isEqualTo(6);
  }

  @Test
  void shouldAddKeysMultipleTimes() {
    // given
    final var batchOperationKey = 1L;
    final var createRecord =
        new BatchOperationCreationRecord()
            .setBatchOperationKey(batchOperationKey)
            .setBatchOperationType(BatchOperationType.PROCESS_CANCELLATION)
            .setFilter(new ProcessInstanceFilter.Builder().build());
    state.create(batchOperationKey, createRecord);

    // when
    state.appendKeys(
        batchOperationKey,
        new BatchOperationSubbatchRecord()
            .setBatchOperationKey(batchOperationKey)
            .setSubbatchKey(2L)
            .setKeys(LongStream.range(0, 99).boxed().toList()));
    state.appendKeys(
        batchOperationKey,
        new BatchOperationSubbatchRecord()
            .setBatchOperationKey(batchOperationKey)
            .setSubbatchKey(3L)
            .setKeys(LongStream.range(100, 199).boxed().toList()));
    state.appendKeys(
        batchOperationKey,
        new BatchOperationSubbatchRecord()
            .setBatchOperationKey(batchOperationKey)
            .setSubbatchKey(4L)
            .setKeys(LongStream.range(200, 299).boxed().toList()));

    // then
    final var persistedBatchOperation = state.get(batchOperationKey).get();

    assertThat(persistedBatchOperation.getMinChunkKey()).isEqualTo(0);
    assertThat(persistedBatchOperation.getMaxChunkKey()).isEqualTo(19);
  }

  @Test
  void shouldMarkKeysAsProcessed() {
    // given
    final var batchOperationKey = createDefaultBatch(1, 100);

    // when
    state.removeKeys(
        batchOperationKey,
        new BatchOperationExecutionRecord()
            .setBatchOperationKey(batchOperationKey)
            .setKeys(LongStream.rangeClosed(0, 5).boxed().collect(Collectors.toSet())));

    // then
    final var persistedBatchOperation = state.get(batchOperationKey).get();

    assertThat(persistedBatchOperation.getMinChunkKey()).isEqualTo(0);
    assertThat(persistedBatchOperation.getMaxChunkKey()).isEqualTo(6);

    final var nextKeys = state.getNextEntityKeys(batchOperationKey, 5);
    assertThat(nextKeys).hasSize(5);
    assertThat(nextKeys).containsSequence(List.of(6L, 7L, 8L, 9L, 10L));
  }

  @Test
  void shouldMarkChunkAsProcessed() {
    // given
    final var batchOperationKey = createDefaultBatch(1, 100);

    // when
    state.removeKeys(
        batchOperationKey,
        new BatchOperationExecutionRecord()
            .setBatchOperationKey(batchOperationKey)
            .setKeys(
                LongStream.rangeClosed(0, MAX_CHUNK_SIZE).boxed().collect(Collectors.toSet())));

    // then
    final var persistedBatchOperation = state.get(batchOperationKey).get();

    assertThat(persistedBatchOperation.getMinChunkKey()).isEqualTo(1);
    assertThat(persistedBatchOperation.getMaxChunkKey()).isEqualTo(6);

    final var nextKeys = state.getNextEntityKeys(batchOperationKey, 3);
    assertThat(nextKeys).containsSequence(List.of(15L, 16L, 17L));
  }

  private long createDefaultBatch(final long batchOperationKey, final long numKeys) {
    state.create(
        batchOperationKey,
        new BatchOperationCreationRecord()
            .setBatchOperationKey(batchOperationKey)
            .setBatchOperationType(BatchOperationType.PROCESS_CANCELLATION)
            .setFilter(new ProcessInstanceFilter.Builder().build()));
    state.appendKeys(
        batchOperationKey,
        new BatchOperationSubbatchRecord()
            .setBatchOperationKey(batchOperationKey)
            .setSubbatchKey(2L)
            .setKeys(LongStream.range(0, numKeys).boxed().toList()));

    return batchOperationKey;
  }
}
