/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.batchoperation;

import static io.camunda.zeebe.engine.state.batchoperation.DbBatchOperationState.MAX_BLOCK_SIZE;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperation.BatchOperationStatus;
import io.camunda.zeebe.engine.state.mutable.MutableBatchOperationState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationChunkRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationExecutionRecord;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public class BatchOperationStateTest {

  private MutableProcessingState processingState;
  private MutableBatchOperationState state;

  @BeforeEach
  public void setup() {
    state = processingState.getBatchOperationState();
  }

  @Test
  void shouldCreateBatchOperation() throws JsonProcessingException {
    // given
    final var batchOperationKey = 1L;
    final var type = BatchOperationType.PROCESS_CANCELLATION;
    final var filter =
        new ProcessInstanceFilter.Builder()
            .processDefinitionIds("process")
            .processDefinitionVersions(1)
            .build();
    final var record =
        new BatchOperationCreationRecord()
            .setBatchOperationKey(batchOperationKey)
            .setBatchOperationType(type)
            .setEntityFilter(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(filter)));

    // when
    state.create(batchOperationKey, record);

    // then
    final var batchOperation = state.get(batchOperationKey).get();
    final var recordFilter =
        new ObjectMapper().readValue(batchOperation.getEntityFilter(), ProcessInstanceFilter.class);

    assertThat(batchOperation.getKey()).isEqualTo(batchOperationKey);
    assertThat(batchOperation.getBatchOperationType()).isEqualTo(type);
    assertThat(recordFilter).isEqualTo(filter);
    assertThat(batchOperation.getStatus()).isEqualTo(BatchOperationStatus.CREATED);
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
    state.appendKeys(
        batchOperationKey,
        new BatchOperationChunkRecord()
            .setBatchOperationKey(batchOperationKey)
            .setChunkKey(2L)
            .setEntityKeys(LongStream.range(0, 99).boxed().toList()));

    // then
    final var pendingKeys = new ArrayList<>();
    state.foreachPendingBatchOperation(bo -> pendingKeys.add(bo.getKey()));

    assertThat(pendingKeys).isEmpty();
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
            .setEntityFilter(convertToBuffer(new ProcessInstanceFilter.Builder().build()));
    state.create(batchOperationKey, createRecord);

    // when
    final var subbatchKey = 2L;
    final var keys = LongStream.rangeClosed(0, 10).boxed().toList();
    final var subbatchRecord =
        new BatchOperationChunkRecord()
            .setBatchOperationKey(batchOperationKey)
            .setChunkKey(subbatchKey)
            .setEntityKeys(keys);
    state.appendKeys(batchOperationKey, subbatchRecord);

    // then
    final var persistedBatchOperation = state.get(batchOperationKey).get();

    assertThat(persistedBatchOperation.getMinBlockKey()).isEqualTo(0);
    assertThat(persistedBatchOperation.getMaxBlockKey()).isEqualTo(0);

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
            .setEntityFilter(convertToBuffer(new ProcessInstanceFilter.Builder().build()));
    state.create(batchOperationKey, createRecord);

    // when
    final var subbatchKey = 2L;
    final var keys = LongStream.rangeClosed(0, 4).boxed().toList();
    final var subbatchRecord =
        new BatchOperationChunkRecord()
            .setBatchOperationKey(batchOperationKey)
            .setChunkKey(subbatchKey)
            .setEntityKeys(keys);
    state.appendKeys(batchOperationKey, subbatchRecord);

    // then
    final var persistedBatchOperation = state.get(batchOperationKey).get();

    assertThat(persistedBatchOperation.getMinBlockKey()).isEqualTo(0);
    assertThat(persistedBatchOperation.getMaxBlockKey()).isEqualTo(0);

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
            .setEntityFilter(convertToBuffer(new ProcessInstanceFilter.Builder().build()));
    state.create(batchOperationKey, createRecord);

    // when
    final var subbatchKey = 2L;
    final var keys = LongStream.range(0, MAX_BLOCK_SIZE * 3 + 1).boxed().toList();
    final var subbatchRecord =
        new BatchOperationChunkRecord()
            .setBatchOperationKey(batchOperationKey)
            .setChunkKey(subbatchKey)
            .setEntityKeys(keys);
    state.appendKeys(batchOperationKey, subbatchRecord);

    // then
    final var persistedBatchOperation = state.get(batchOperationKey).get();

    assertThat(persistedBatchOperation.getMinBlockKey()).isEqualTo(0);
    assertThat(persistedBatchOperation.getMaxBlockKey()).isEqualTo(3);
  }

  @Test
  void shouldAddKeysMultipleTimes() {
    // given
    final var batchOperationKey = 1L;
    final var createRecord =
        new BatchOperationCreationRecord()
            .setBatchOperationKey(batchOperationKey)
            .setBatchOperationType(BatchOperationType.PROCESS_CANCELLATION)
            .setEntityFilter(convertToBuffer(new ProcessInstanceFilter.Builder().build()));
    state.create(batchOperationKey, createRecord);

    // when
    state.appendKeys(
        batchOperationKey,
        new BatchOperationChunkRecord()
            .setBatchOperationKey(batchOperationKey)
            .setChunkKey(2L)
            .setEntityKeys(LongStream.range(0, MAX_BLOCK_SIZE - 1).boxed().toList()));
    state.appendKeys(
        batchOperationKey,
        new BatchOperationChunkRecord()
            .setBatchOperationKey(batchOperationKey)
            .setChunkKey(3L)
            .setEntityKeys(
                LongStream.range(MAX_BLOCK_SIZE, MAX_BLOCK_SIZE * 2 - 1).boxed().toList()));
    state.appendKeys(
        batchOperationKey,
        new BatchOperationChunkRecord()
            .setBatchOperationKey(batchOperationKey)
            .setChunkKey(4L)
            .setEntityKeys(
                LongStream.range(MAX_BLOCK_SIZE * 2, MAX_BLOCK_SIZE * 3 - 1).boxed().toList()));

    // then
    final var persistedBatchOperation = state.get(batchOperationKey).get();

    assertThat(persistedBatchOperation.getMinBlockKey()).isEqualTo(0);
    assertThat(persistedBatchOperation.getMaxBlockKey()).isEqualTo(2);
  }

  @Test
  void shouldReturnNextNonRemovedItems() {
    // given
    final var batchOperationKey = createDefaultBatch(1, MAX_BLOCK_SIZE * 3);

    // when
    state.removeKeys(
        batchOperationKey,
        new BatchOperationExecutionRecord()
            .setBatchOperationKey(batchOperationKey)
            .setEntityKeys(LongStream.rangeClosed(0, 5).boxed().collect(Collectors.toSet())));

    // then
    final var persistedBatchOperation = state.get(batchOperationKey).get();

    assertThat(persistedBatchOperation.getMinBlockKey()).isEqualTo(0);
    assertThat(persistedBatchOperation.getMaxBlockKey()).isEqualTo(2);

    final var nextKeys = state.getNextEntityKeys(batchOperationKey, 5);
    assertThat(nextKeys).hasSize(5);
    assertThat(nextKeys).containsSequence(List.of(6L, 7L, 8L, 9L, 10L));
  }

  @Test
  void shouldRemoveChunkWhenAllItemsHasBeenRemoved() {
    // given
    final var batchOperationKey = createDefaultBatch(1, MAX_BLOCK_SIZE * 3);

    // when
    state.removeKeys(
        batchOperationKey,
        new BatchOperationExecutionRecord()
            .setBatchOperationKey(batchOperationKey)
            .setEntityKeys(
                LongStream.range(0, MAX_BLOCK_SIZE).boxed().collect(Collectors.toSet())));

    // then
    final var persistedBatchOperation = state.get(batchOperationKey).get();

    assertThat(persistedBatchOperation.getMinBlockKey()).isEqualTo(1);
    assertThat(persistedBatchOperation.getMaxBlockKey()).isEqualTo(2);

    final var nextKeys = state.getNextEntityKeys(batchOperationKey, 3);
    assertThat(nextKeys)
        .containsSequence(List.of(MAX_BLOCK_SIZE, MAX_BLOCK_SIZE + 1L, MAX_BLOCK_SIZE + 2L));
  }

  private long createDefaultBatch(final long batchOperationKey, final long numKeys) {
    state.create(
        batchOperationKey,
        new BatchOperationCreationRecord()
            .setBatchOperationKey(batchOperationKey)
            .setBatchOperationType(BatchOperationType.PROCESS_CANCELLATION)
            .setEntityFilter(convertToBuffer(new ProcessInstanceFilter.Builder().build())));
    state.appendKeys(
        batchOperationKey,
        new BatchOperationChunkRecord()
            .setBatchOperationKey(batchOperationKey)
            .setChunkKey(2L)
            .setEntityKeys(LongStream.range(0, numKeys).boxed().toList()));

    return batchOperationKey;
  }

  private static UnsafeBuffer convertToBuffer(final Object object) {
    return new UnsafeBuffer(MsgPackConverter.convertToMsgPack(object));
  }
}
