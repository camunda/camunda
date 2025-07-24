/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.google.common.collect.Lists;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationChunkIntent;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

public final class CreateBatchOperationTest extends AbstractBatchOperationTest {

  @Test
  public void shouldRejectWithoutFilter() {
    // when
    final var batchOperationKey =
        engine
            .batchOperation()
            .newCreation(BatchOperationType.CANCEL_PROCESS_INSTANCE)
            .withFilter(new UnsafeBuffer())
            .expectRejection()
            .create()
            .getValue()
            .getBatchOperationKey();

    // then
    final var result =
        RecordingExporter.batchOperationCreationRecords()
            .withBatchOperationKey(batchOperationKey)
            .onlyCommandRejections()
            .getFirst();

    assertThat(result.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(result.getIntent()).isEqualTo(BatchOperationIntent.CREATE);
  }

  @Test
  public void shouldRejectWithEmptyFilter() {
    // when
    final var batchOperationKey =
        engine
            .batchOperation()
            .newCreation(BatchOperationType.CANCEL_PROCESS_INSTANCE)
            .withFilter(new UnsafeBuffer(MsgPackConverter.convertToMsgPack("{}")))
            .expectRejection()
            .create()
            .getValue()
            .getBatchOperationKey();

    // then
    final var result =
        RecordingExporter.batchOperationCreationRecords()
            .withBatchOperationKey(batchOperationKey)
            .onlyCommandRejections()
            .getFirst();

    assertThat(result.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(result.getIntent()).isEqualTo(BatchOperationIntent.CREATE);
  }

  @Test
  public void shouldCreateAndInitBatchOperation() {
    // given
    final var filterBuffer =
        convertToBuffer(
            new ProcessInstanceFilter.Builder().processInstanceKeys(1L, 3L, 8L).build());

    // given
    final var result =
        new SearchQueryResult.Builder<ProcessInstanceEntity>()
            .items(
                List.of(
                    fakeProcessInstanceEntity(1L),
                    fakeProcessInstanceEntity(2L),
                    fakeProcessInstanceEntity(3L)))
            .total(3)
            .build();

    when(searchClientsProxy.searchProcessInstances(any(ProcessInstanceQuery.class)))
        .thenReturn(result);

    // when
    final long batchOperationKey =
        engine
            .batchOperation()
            .newCreation(BatchOperationType.CANCEL_PROCESS_INSTANCE)
            .withFilter(filterBuffer)
            .create()
            .getValue()
            .getBatchOperationKey();

    // then
    assertThat(
            RecordingExporter.batchOperationCreationRecords()
                .withBatchOperationKey(batchOperationKey)
                .limit(record -> record.getIntent().equals(BatchOperationIntent.CREATED)))
        .extracting(Record::getIntent)
        .containsSequence(BatchOperationIntent.CREATED);

    assertThat(
            RecordingExporter.batchOperationChunkRecords()
                .withBatchOperationKey(batchOperationKey)
                .limit(record -> record.getIntent().equals(BatchOperationChunkIntent.CREATED)))
        .extracting(Record::getIntent)
        .containsSequence(BatchOperationChunkIntent.CREATE, BatchOperationChunkIntent.CREATED);
  }

  @Test
  public void shouldCreateAndInitLargeBatchOperation() {
    // given
    final int numItems = 200000;
    final var filterBuffer =
        convertToBuffer(
            new ProcessInstanceFilter.Builder().processInstanceKeys(1L, 3L, 8L).build());

    mockSearchClientWithMultiplePages(numItems);

    // when
    final long batchOperationKey =
        engine
            .batchOperation()
            .newCreation(BatchOperationType.CANCEL_PROCESS_INSTANCE)
            .withFilter(filterBuffer)
            .create()
            .getValue()
            .getBatchOperationKey();

    // then
    assertThat(
            RecordingExporter.batchOperationCreationRecords()
                .withBatchOperationKey(batchOperationKey)
                .limit(record -> record.getIntent().equals(BatchOperationIntent.CREATED)))
        .extracting(Record::getIntent)
        .containsSequence(BatchOperationIntent.CREATED);

    final var expectedChunks =
        Math.round(
            Math.ceil(numItems / (double) EngineConfiguration.DEFAULT_BATCH_OPERATION_CHUNK_SIZE));
    final var chunkRecordStream =
        RecordingExporter.batchOperationChunkRecords()
            .withBatchOperationKey(batchOperationKey)
            .withIntent(BatchOperationChunkIntent.CREATED)
            .limit(expectedChunks)
            .toList();
    assertThat(chunkRecordStream).hasSize((int) expectedChunks);
    assertThat(
            chunkRecordStream.stream()
                .map(r -> r.getValue().getItems().size())
                .reduce(0, Integer::sum))
        .isEqualTo(numItems);
  }

  @Test
  public void shouldBeAuthorizedToCreateBatchOperationWithAdminPermission() {
    // given
    final var user = createUser();
    addPermissionsToUser(user, PermissionType.CREATE);

    // when
    final var batchOperationRecord =
        engine
            .batchOperation()
            .newCreation(BatchOperationType.CANCEL_PROCESS_INSTANCE)
            .withFilter(
                new UnsafeBuffer(
                    MsgPackConverter.convertToMsgPack(new ProcessInstanceFilter.Builder().build())))
            .create(user.getUsername());

    // then
    assertThat(batchOperationRecord.getRejectionType()).isEqualTo(RejectionType.NULL_VAL);
  }

  @Test
  public void shouldBeUnauthorizedToCreateBatchOperationWithNoPermissions() {
    // given
    final var user = createUser();

    // when
    final var batchOperationRecord =
        engine
            .batchOperation()
            .newCreation(BatchOperationType.CANCEL_PROCESS_INSTANCE)
            .expectRejection()
            .withFilter(
                new UnsafeBuffer(
                    MsgPackConverter.convertToMsgPack(new ProcessInstanceFilter.Builder().build())))
            .create(user.getUsername());

    // then
    assertThat(batchOperationRecord.getRejectionType()).isEqualTo(RejectionType.FORBIDDEN);
  }

  @Test
  public void shouldBeAuthorizedToCreateWithSpecificPermissionCancelProcessInstance() {
    checkAuthorized(
        BatchOperationType.CANCEL_PROCESS_INSTANCE,
        PermissionType.CREATE_BATCH_OPERATION_CANCEL_PROCESS_INSTANCE);
  }

  @Test
  public void shouldBeAuthorizedToCreateWithSpecificPermissionMigrateProcessInstance() {
    checkAuthorized(
        BatchOperationType.MIGRATE_PROCESS_INSTANCE,
        PermissionType.CREATE_BATCH_OPERATION_MIGRATE_PROCESS_INSTANCE);
  }

  @Test
  public void shouldBeAuthorizedToCreateWithSpecificPermissionModifyProcessInstance() {
    checkAuthorized(
        BatchOperationType.MODIFY_PROCESS_INSTANCE,
        PermissionType.CREATE_BATCH_OPERATION_MODIFY_PROCESS_INSTANCE);
  }

  @Test
  public void shouldBeAuthorizedToCreateWithSpecificPermissionResolveIncident() {
    checkAuthorized(
        BatchOperationType.RESOLVE_INCIDENT,
        PermissionType.CREATE_BATCH_OPERATION_RESOLVE_INCIDENT);
  }

  public void checkAuthorized(
      final BatchOperationType batchOperationType, final PermissionType permissionType) {
    // given
    final var user = createUser();
    addPermissionsToUser(user, permissionType);

    // when
    final var batchOperationRecord =
        engine
            .batchOperation()
            .newCreation(batchOperationType)
            .withFilter(
                new UnsafeBuffer(
                    MsgPackConverter.convertToMsgPack(new ProcessInstanceFilter.Builder().build())))
            .create(user.getUsername());

    // then
    assertThat(batchOperationRecord.getRejectionType()).isEqualTo(RejectionType.NULL_VAL);
  }

  private void mockSearchClientWithMultiplePages(final int numItems) {
    // crate pages of fake process instances
    final var itemPages =
        Lists.partition(
            LongStream.range(0, numItems).boxed().map(this::fakeProcessInstanceEntity).toList(),
            DEFAULT_QUERY_PAGE_SIZE);

    // now create a map of item pages with their respective after-cursors
    final Map<String, SearchQueryResult<ProcessInstanceEntity>> itemPagesMap = new HashMap<>();
    for (int i = 0; i < itemPages.size(); i++) {
      final var result =
          new SearchQueryResult.Builder<ProcessInstanceEntity>()
              .items(itemPages.get(i))
              .total(numItems)
              .endCursor(String.valueOf(i))
              .build();
      itemPagesMap.put(String.valueOf(i), result);
    }
    // append a last empty page to indicate the end of the results
    final var emptyResult =
        new SearchQueryResult.Builder<ProcessInstanceEntity>()
            .items(List.of())
            .total(numItems)
            .build();
    itemPagesMap.put(String.valueOf(itemPages.size()), emptyResult);

    // mock the search client to return the pages based on the after-cursor
    when(searchClientsProxy.searchProcessInstances(any(ProcessInstanceQuery.class)))
        .then(
            invocation -> {
              final var after =
                  invocation.getArgument(0, ProcessInstanceQuery.class).page().after();
              final var page = after == null ? "-1" : after;
              return itemPagesMap.get(Integer.toString(Integer.parseInt(page) + 1));
            });
  }
}
