/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.batchoperation;

import static io.camunda.zeebe.engine.state.batchoperation.DbBatchOperationState.MAX_DB_CHUNK_SIZE;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperation.BatchOperationStatus;
import io.camunda.zeebe.engine.state.mutable.MutableBatchOperationState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationError;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationProcessInstanceMigrationPlan;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationProcessInstanceModificationMoveInstruction;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationProcessInstanceModificationPlan;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceMigrationMappingInstruction;
import io.camunda.zeebe.protocol.record.value.BatchOperationErrorType;
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
    final var type = BatchOperationType.CANCEL_PROCESS_INSTANCE;
    final var filter =
        new ProcessInstanceFilter.Builder()
            .processDefinitionIds("process")
            .processDefinitionVersions(1)
            .build();
    final String username = "bud spencer";
    final var authentication = CamundaAuthentication.of(b -> b.user(username));
    final var record =
        new BatchOperationCreationRecord()
            .setBatchOperationKey(batchOperationKey)
            .setBatchOperationType(type)
            .setEntityFilter(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(filter)))
            .setAuthentication(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(authentication)));

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
    assertThat(batchOperation.getAuthentication()).isEqualTo(authentication);
    assertThat(batchOperation.isInitialized()).isFalse();
    assertThat(batchOperation.getNumTotalItems()).isEqualTo(0);
    assertThat(batchOperation.getNumExecutedItems()).isEqualTo(0);
  }

  @Test
  void shouldCreateBatchOperationForMigration() throws JsonProcessingException {
    // given
    final var batchOperationKey = 1L;
    final var type = BatchOperationType.MIGRATE_PROCESS_INSTANCE;
    final var filter =
        new ProcessInstanceFilter.Builder()
            .processDefinitionIds("process")
            .processDefinitionVersions(1)
            .build();

    final var migrationPlan = new BatchOperationProcessInstanceMigrationPlan();
    migrationPlan.setTargetProcessDefinitionKey(2L);
    final var mappingInstruction =
        new ProcessInstanceMigrationMappingInstruction()
            .setSourceElementId("source")
            .setTargetElementId("target");
    migrationPlan.addMappingInstruction(mappingInstruction);

    final var record =
        new BatchOperationCreationRecord()
            .setBatchOperationKey(batchOperationKey)
            .setBatchOperationType(type)
            .setEntityFilter(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(filter)))
            .setMigrationPlan(migrationPlan);

    // when
    state.create(batchOperationKey, record);

    // then
    final var batchOperation = state.get(batchOperationKey).get();
    final var recordFilter =
        new ObjectMapper().readValue(batchOperation.getEntityFilter(), ProcessInstanceFilter.class);

    assertThat(batchOperation.getKey()).isEqualTo(batchOperationKey);
    assertThat(batchOperation.getBatchOperationType()).isEqualTo(type);
    assertThat(recordFilter).isEqualTo(filter);
    assertThat(batchOperation.getMigrationPlan()).isEqualTo(migrationPlan);
    assertThat(batchOperation.getStatus()).isEqualTo(BatchOperationStatus.CREATED);
  }

  @Test
  void shouldCreateBatchOperationForModification() throws JsonProcessingException {
    // given
    final var batchOperationKey = 1L;
    final var type = BatchOperationType.MODIFY_PROCESS_INSTANCE;
    final var filter =
        new ProcessInstanceFilter.Builder()
            .processDefinitionIds("process")
            .processDefinitionVersions(1)
            .build();

    final var modificationPlan = new BatchOperationProcessInstanceModificationPlan();
    final var mappingInstruction =
        new BatchOperationProcessInstanceModificationMoveInstruction()
            .setSourceElementId("source")
            .setTargetElementId("target");
    modificationPlan.addMoveInstruction(mappingInstruction);

    final var record =
        new BatchOperationCreationRecord()
            .setBatchOperationKey(batchOperationKey)
            .setBatchOperationType(type)
            .setEntityFilter(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(filter)))
            .setModificationPlan(modificationPlan);

    // when
    state.create(batchOperationKey, record);

    // then
    final var batchOperation = state.get(batchOperationKey).get();
    final var recordFilter =
        new ObjectMapper().readValue(batchOperation.getEntityFilter(), ProcessInstanceFilter.class);

    assertThat(batchOperation.getKey()).isEqualTo(batchOperationKey);
    assertThat(batchOperation.getBatchOperationType()).isEqualTo(type);
    assertThat(recordFilter).isEqualTo(filter);
    assertThat(batchOperation.getModificationPlan()).isEqualTo(modificationPlan);
    assertThat(batchOperation.getStatus()).isEqualTo(BatchOperationStatus.CREATED);
  }

  @Test
  void newBatchShouldBePending() {
    // given

    // when
    final var batchOperationKey = 1L;
    final var batchRecord =
        new BatchOperationCreationRecord()
            .setBatchOperationKey(batchOperationKey)
            .setBatchOperationType(BatchOperationType.CANCEL_PROCESS_INSTANCE);
    state.create(batchOperationKey, batchRecord);

    // then
    assertThat(state.getNextPendingBatchOperation().get().getKey()).isEqualTo(batchOperationKey);
  }

  @Test
  void startedBatchShouldNotBePending() {
    // given
    final var batchOperationKey = 1L;
    final var batchRecord =
        new BatchOperationCreationRecord()
            .setBatchOperationKey(batchOperationKey)
            .setBatchOperationType(BatchOperationType.CANCEL_PROCESS_INSTANCE);
    state.create(batchOperationKey, batchRecord);

    // when
    state.transitionToInitialized(batchOperationKey);

    // then
    assertThat(state.getNextPendingBatchOperation()).isEmpty();

    // and should have status STARTED
    final var persistedBatchOperation = state.get(batchOperationKey).get();
    assertThat(persistedBatchOperation.getStatus()).isEqualTo(BatchOperationStatus.INITIALIZED);
  }

  @Test
  void batchShouldBePendingWhenInitializationContinues() {
    // given
    final var batchOperationKey = 1L;
    final var batchRecord =
        new BatchOperationCreationRecord()
            .setBatchOperationKey(batchOperationKey)
            .setBatchOperationType(BatchOperationType.CANCEL_PROCESS_INSTANCE);
    state.create(batchOperationKey, batchRecord);
    state.transitionToInitialized(1L);

    // when
    state.continueInitialization(batchOperationKey, "123", 100);

    // then
    assertThat(state.getNextPendingBatchOperation().get().getKey()).isEqualTo(batchOperationKey);
    assertThat(state.get(1).get().getInitializationSearchCursor()).isEqualTo("123");
    assertThat(state.get(1).get().getInitializationSearchQueryPageSize()).isEqualTo(100);
  }

  @Test
  void startedBatchShouldBeInitialized() {
    // given
    final var batchOperationKey = 1L;
    final var batchRecord =
        new BatchOperationCreationRecord()
            .setBatchOperationKey(batchOperationKey)
            .setBatchOperationType(BatchOperationType.CANCEL_PROCESS_INSTANCE);
    state.create(batchOperationKey, batchRecord);

    // when
    state.transitionToInitialized(batchOperationKey);

    // then
    final var pendingKeys = new ArrayList<>();
    assertThat(state.get(batchOperationKey).get().isInitialized()).isTrue();
  }

  @Test
  void failedBatchShouldNotBePending() {
    // given
    final var batchOperationKey = 1L;
    final var roleRecord =
        new BatchOperationCreationRecord()
            .setBatchOperationKey(batchOperationKey)
            .setBatchOperationType(BatchOperationType.CANCEL_PROCESS_INSTANCE);
    state.create(batchOperationKey, roleRecord);

    // when
    state.fail(batchOperationKey);

    // then
    assertThat(state.getNextPendingBatchOperation()).isEmpty();

    // and should have status STARTED
    final var persistedBatchOperation = state.get(batchOperationKey).get();
    assertThat(persistedBatchOperation.getStatus()).isEqualTo(BatchOperationStatus.FAILED);
  }

  @Test
  void shouldReturnNoKeysIfEmpty() {
    // given
    final var batchOperationKey = createDefaultBatch(1, 0);

    // when
    final var keys = state.getNextItemKeys(batchOperationKey, 5);

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
            .setBatchOperationType(BatchOperationType.CANCEL_PROCESS_INSTANCE)
            .setEntityFilter(convertToBuffer(new ProcessInstanceFilter.Builder().build()));
    state.create(batchOperationKey, createRecord);

    // when
    final var keys = LongStream.range(0, 10).boxed().collect(Collectors.toSet());
    state.appendItemKeys(batchOperationKey, keys);

    // then
    final var persistedBatchOperation = state.get(batchOperationKey).get();

    assertThat(persistedBatchOperation.getMinChunkKey()).isEqualTo(0);
    assertThat(persistedBatchOperation.getMaxChunkKey()).isEqualTo(0);
    assertThat(persistedBatchOperation.getNumTotalItems()).isEqualTo(10);
    assertThat(persistedBatchOperation.getNumExecutedItems()).isEqualTo(0);

    final var nextKeys = state.getNextItemKeys(batchOperationKey, 5);
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
            .setBatchOperationType(BatchOperationType.CANCEL_PROCESS_INSTANCE)
            .setEntityFilter(convertToBuffer(new ProcessInstanceFilter.Builder().build()));
    state.create(batchOperationKey, createRecord);

    // when
    final var keys = LongStream.rangeClosed(0, 4).boxed().collect(Collectors.toSet());
    state.appendItemKeys(batchOperationKey, keys);

    // then
    final var persistedBatchOperation = state.get(batchOperationKey).get();

    assertThat(persistedBatchOperation.getMinChunkKey()).isEqualTo(0);
    assertThat(persistedBatchOperation.getMaxChunkKey()).isEqualTo(0);

    final var nextKeys = state.getNextItemKeys(batchOperationKey, 10);
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
            .setBatchOperationType(BatchOperationType.CANCEL_PROCESS_INSTANCE)
            .setEntityFilter(convertToBuffer(new ProcessInstanceFilter.Builder().build()));
    state.create(batchOperationKey, createRecord);

    // when
    final var keys =
        LongStream.range(0, MAX_DB_CHUNK_SIZE * 3 + 1).boxed().collect(Collectors.toSet());
    state.appendItemKeys(batchOperationKey, keys);

    // then
    final var persistedBatchOperation = state.get(batchOperationKey).get();

    assertThat(persistedBatchOperation.getMinChunkKey()).isEqualTo(0);
    assertThat(persistedBatchOperation.getMaxChunkKey()).isEqualTo(3);
  }

  @Test
  void shouldAddKeysMultipleTimes() {
    // given
    final var batchOperationKey = 1L;
    final var createRecord =
        new BatchOperationCreationRecord()
            .setBatchOperationKey(batchOperationKey)
            .setBatchOperationType(BatchOperationType.CANCEL_PROCESS_INSTANCE)
            .setEntityFilter(convertToBuffer(new ProcessInstanceFilter.Builder().build()));
    state.create(batchOperationKey, createRecord);

    // when
    state.appendItemKeys(
        batchOperationKey,
        LongStream.range(0, MAX_DB_CHUNK_SIZE).boxed().collect(Collectors.toSet()));
    state.appendItemKeys(
        batchOperationKey,
        LongStream.range(MAX_DB_CHUNK_SIZE, MAX_DB_CHUNK_SIZE * 2)
            .boxed()
            .collect(Collectors.toSet()));
    state.appendItemKeys(
        batchOperationKey,
        LongStream.range(MAX_DB_CHUNK_SIZE * 2, MAX_DB_CHUNK_SIZE * 3)
            .boxed()
            .collect(Collectors.toSet()));

    // then
    final var persistedBatchOperation = state.get(batchOperationKey).get();

    assertThat(persistedBatchOperation.getMinChunkKey()).isEqualTo(0);
    assertThat(persistedBatchOperation.getMaxChunkKey()).isEqualTo(2);
    assertThat(persistedBatchOperation.getNumTotalItems()).isEqualTo(MAX_DB_CHUNK_SIZE * 3);
    assertThat(persistedBatchOperation.getNumExecutedItems()).isEqualTo(0);
  }

  @Test
  void shouldReturnNextNonRemovedItems() {
    // given
    final var batchOperationKey = createDefaultBatch(1, MAX_DB_CHUNK_SIZE * 3);

    // when
    state.removeItemKeys(
        batchOperationKey, LongStream.rangeClosed(0, 5).boxed().collect(Collectors.toSet()));

    // then
    final var persistedBatchOperation = state.get(batchOperationKey).get();

    assertThat(persistedBatchOperation.getMinChunkKey()).isEqualTo(0);
    assertThat(persistedBatchOperation.getMaxChunkKey()).isEqualTo(2);

    final var nextItemKeys = state.getNextItemKeys(batchOperationKey, 5);
    assertThat(nextItemKeys).hasSize(5);
    assertThat(nextItemKeys).containsSequence(List.of(6L, 7L, 8L, 9L, 10L));
    assertThat(persistedBatchOperation.getNumTotalItems()).isEqualTo(MAX_DB_CHUNK_SIZE * 3);
  }

  @Test
  void shouldRemoveChunkWhenAllItemsHasBeenRemoved() {
    // given
    final var batchOperationKey = createDefaultBatch(1, MAX_DB_CHUNK_SIZE * 3);

    // when
    state.removeItemKeys(
        batchOperationKey,
        LongStream.range(0, MAX_DB_CHUNK_SIZE).boxed().collect(Collectors.toSet()));

    // then
    final var persistedBatchOperation = state.get(batchOperationKey).get();

    assertThat(persistedBatchOperation.getMinChunkKey()).isEqualTo(1);
    assertThat(persistedBatchOperation.getMaxChunkKey()).isEqualTo(2);

    final var nextItemKeys = state.getNextItemKeys(batchOperationKey, 3);
    assertThat(nextItemKeys)
        .containsSequence(
            List.of(MAX_DB_CHUNK_SIZE, MAX_DB_CHUNK_SIZE + 1L, MAX_DB_CHUNK_SIZE + 2L));
  }

  private long createDefaultBatch(final long batchOperationKey, final long numKeys) {
    state.create(
        batchOperationKey,
        new BatchOperationCreationRecord()
            .setBatchOperationKey(batchOperationKey)
            .setBatchOperationType(BatchOperationType.CANCEL_PROCESS_INSTANCE)
            .setEntityFilter(convertToBuffer(new ProcessInstanceFilter.Builder().build())));
    state.appendItemKeys(
        batchOperationKey, LongStream.range(0, numKeys).boxed().collect(Collectors.toSet()));

    return batchOperationKey;
  }

  @Test
  void completedBatchShouldBeRemoved() {
    // given
    final var batchOperationKey = 1L;
    final var batchRecord =
        new BatchOperationCreationRecord()
            .setBatchOperationKey(batchOperationKey)
            .setBatchOperationType(BatchOperationType.CANCEL_PROCESS_INSTANCE);
    state.create(batchOperationKey, batchRecord);

    // when
    state.complete(batchOperationKey);

    // then
    assertThat(state.get(batchOperationKey)).isEmpty();
  }

  @Test
  void canceledBatchShouldBeRemoved() {
    // given
    final var batchOperationKey = 1L;
    final var batchRecord =
        new BatchOperationCreationRecord()
            .setBatchOperationKey(batchOperationKey)
            .setBatchOperationType(BatchOperationType.CANCEL_PROCESS_INSTANCE);
    state.create(batchOperationKey, batchRecord);

    // when
    state.cancel(batchOperationKey);

    // then
    assertThat(state.get(batchOperationKey)).isEmpty();
    assertThat(state.getNextItemKeys(batchOperationKey, 20)).isEmpty();
  }

  @Test
  void shouldReturnTrueForCanSuspend() {
    // given
    final var batchOperationKey = 1L;
    final var batchRecord =
        new BatchOperationCreationRecord()
            .setBatchOperationKey(batchOperationKey)
            .setBatchOperationType(BatchOperationType.CANCEL_PROCESS_INSTANCE);

    // when
    state.create(batchOperationKey, batchRecord);

    // then
    final var persistedBatchOperation = state.get(batchOperationKey);
    assertThat(persistedBatchOperation).isNotEmpty();
    assertThat(persistedBatchOperation.get().canSuspend()).isTrue();
  }

  @Test
  void shouldReturnFalseForCanSuspend() {
    // given
    final var batchOperationKey = 1L;
    final var batchRecord =
        new BatchOperationCreationRecord()
            .setBatchOperationKey(batchOperationKey)
            .setBatchOperationType(BatchOperationType.CANCEL_PROCESS_INSTANCE);

    state.create(batchOperationKey, batchRecord);

    // when
    state.fail(batchOperationKey);

    // then
    final var persistedBatchOperation = state.get(batchOperationKey);
    assertThat(persistedBatchOperation).isNotEmpty();
    assertThat(persistedBatchOperation.get().canSuspend()).isFalse();
  }

  @Test
  void shouldSuspendBatch() {
    // given
    final var batchOperationKey = 1L;
    final var batchRecord =
        new BatchOperationCreationRecord()
            .setBatchOperationKey(batchOperationKey)
            .setBatchOperationType(BatchOperationType.CANCEL_PROCESS_INSTANCE);
    state.create(batchOperationKey, batchRecord);

    // when
    state.suspend(batchOperationKey);

    // then
    final var persistedBatchOperation = state.get(batchOperationKey);
    assertThat(persistedBatchOperation).isNotEmpty();
    assertThat(persistedBatchOperation.get().isSuspended()).isTrue();
  }

  @Test
  void shouldResumeCreatedBatch() {
    // given
    final var batchOperationKey = 1L;
    final var batchRecord =
        new BatchOperationCreationRecord()
            .setBatchOperationKey(batchOperationKey)
            .setBatchOperationType(BatchOperationType.CANCEL_PROCESS_INSTANCE);
    state.create(batchOperationKey, batchRecord);
    state.suspend(batchOperationKey);

    // when
    state.resume(batchOperationKey);

    // then
    final var persistedBatchOperation = state.get(batchOperationKey);
    assertThat(persistedBatchOperation).isNotEmpty();
    assertThat(persistedBatchOperation.get().getStatus()).isEqualTo(BatchOperationStatus.CREATED);
  }

  @Test
  void shouldResumeInitializedBatch() {
    // given
    final var batchOperationKey = 1L;
    final var batchRecord =
        new BatchOperationCreationRecord()
            .setBatchOperationKey(batchOperationKey)
            .setBatchOperationType(BatchOperationType.CANCEL_PROCESS_INSTANCE);
    state.create(batchOperationKey, batchRecord);
    state.transitionToInitialized(batchOperationKey);
    state.suspend(batchOperationKey);

    // when
    state.resume(batchOperationKey);

    // then
    final var persistedBatchOperation = state.get(batchOperationKey);
    assertThat(persistedBatchOperation).isNotEmpty();
    assertThat(persistedBatchOperation.get().getStatus())
        .isEqualTo(BatchOperationStatus.INITIALIZED);
  }

  @Test
  void shouldReturnTrueForResumeBatch() {
    // given
    final var batchOperationKey = 1L;
    final var batchRecord =
        new BatchOperationCreationRecord()
            .setBatchOperationKey(batchOperationKey)
            .setBatchOperationType(BatchOperationType.CANCEL_PROCESS_INSTANCE);
    state.create(batchOperationKey, batchRecord);
    state.transitionToInitialized(batchOperationKey);
    state.suspend(batchOperationKey);

    // when
    final var persistedBatchOperation = state.get(batchOperationKey).get();
    final var canResume = persistedBatchOperation.canResume();

    // then

    assertThat(canResume).isTrue();
  }

  @Test
  void shouldReturnFalseForResumeBatch() {
    // given
    final var batchOperationKey = 1L;
    final var batchRecord =
        new BatchOperationCreationRecord()
            .setBatchOperationKey(batchOperationKey)
            .setBatchOperationType(BatchOperationType.CANCEL_PROCESS_INSTANCE);
    state.create(batchOperationKey, batchRecord);
    state.fail(batchOperationKey);

    // when
    final var persistedBatchOperation = state.get(batchOperationKey).get();
    final var canResume = persistedBatchOperation.canResume();

    // then

    assertThat(canResume).isFalse();
  }

  private static UnsafeBuffer convertToBuffer(final Object object) {
    return new UnsafeBuffer(MsgPackConverter.convertToMsgPack(object));
  }

  @Test
  void shouldFailPartitionAndTrackError() {
    // given
    final var batchOperationKey = 1L;
    final var partitionId = 0;
    final var errorType = BatchOperationErrorType.QUERY_FAILED;
    final var stacktrace = "BANG";
    final var batchRecord =
        new BatchOperationCreationRecord()
            .setBatchOperationKey(batchOperationKey)
            .setBatchOperationType(BatchOperationType.CANCEL_PROCESS_INSTANCE);
    state.create(batchOperationKey, batchRecord);

    final var error = new BatchOperationError();
    error.setPartitionId(partitionId);
    error.setType(errorType);
    error.setMessage(stacktrace);

    // when
    state.failPartition(batchOperationKey, partitionId, error);

    // then
    final var batchOperation = state.get(batchOperationKey).get();
    final var errors = batchOperation.getErrors();
    assertThat(errors).hasSize(1);
    final var firstError = errors.getFirst();
    assertThat(firstError.getPartitionId()).isEqualTo(partitionId);
    assertThat(firstError.getType()).isEqualTo(errorType);
    assertThat(firstError.getMessage()).isEqualTo(stacktrace);
  }
}
