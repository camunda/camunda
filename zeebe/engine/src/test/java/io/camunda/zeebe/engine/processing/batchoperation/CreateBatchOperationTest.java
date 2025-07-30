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

import io.camunda.search.entities.IncidentEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.filter.Operation;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.search.query.IncidentQuery;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationChunkIntent;
import io.camunda.zeebe.protocol.record.intent.BatchOperationExecutionIntent;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
  public void shouldCreateAndInitLargeResolveIncidentBatchOperation() {
    // given
    final int numItems = 3000;
    final var filterBuffer =
        convertToBuffer(
            new ProcessInstanceFilter.Builder().processInstanceKeys(1L, 3L, 8L).build());

    mockSearchClientWithMultiplePages(numItems);

    // when
    final long batchOperationKey =
        engine
            .batchOperation()
            .newCreation(BatchOperationType.RESOLVE_INCIDENT)
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

    // then
    assertThat(
            RecordingExporter.batchOperationInitializationRecords()
                .withBatchOperationKey(batchOperationKey)
                .onlyEvents()
                .limitByCount(
                    record -> record.getIntent().equals(BatchOperationIntent.INITIALIZING),
                    4)) // reduce to 5000, 2500, 1250 and then one more init-phase
        .extracting(r -> r.getValue().getSearchQueryPageSize())
        .containsSequence(5000, 2500, 1250, 1250);

    assertThat(
            RecordingExporter.batchOperationExecutionRecords()
                .withBatchOperationKey(batchOperationKey)
                .limit(record -> record.getIntent().equals(BatchOperationExecutionIntent.EXECUTE)))
        .extracting(Record::getIntent)
        .containsSequence(BatchOperationExecutionIntent.EXECUTE);
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
    final var items =
        LongStream.rangeClosed(1, numItems).boxed().map(this::fakeProcessInstanceEntity).toList();

    // append a last empty page to indicate the end of the results
    final var emptyResult =
        new SearchQueryResult.Builder<ProcessInstanceEntity>()
            .items(List.of())
            .total(numItems)
            .build();

    // mock the search client to return the pages based on the after-cursor
    when(searchClientsProxy.searchProcessInstances(any(ProcessInstanceQuery.class)))
        .then(
            invocation -> {
              final var filter = invocation.getArgument(0, ProcessInstanceQuery.class);
              final var after =
                  Integer.parseInt(Objects.requireNonNullElse(filter.page().after(), "0"));
              final var itemsPerPage = filter.page().size();

              if (after >= numItems) {
                return emptyResult; // return empty result if after exceeds total items
              }

              final var pageItems =
                  items.subList(after, Math.min(after + itemsPerPage, items.size()));

              return new SearchQueryResult.Builder<ProcessInstanceEntity>()
                  .items(pageItems)
                  .total(numItems)
                  .endCursor(Long.toString(pageItems.getLast().processInstanceKey()))
                  .build();
            });

    // mock the search client to return the incidents based on the processInstanceKeys in the filter
    when(searchClientsProxy.searchIncidents(any(IncidentQuery.class)))
        .then(
            invocation -> {
              final var query = invocation.getArgument(0, IncidentQuery.class);

              if (query.page().after() != null) {
                // If after cursor is provided, return an empty result
                return new SearchQueryResult.Builder<IncidentEntity>()
                    .items(List.of())
                    .total(0)
                    .endCursor(null)
                    .build();
              }

              final var processInstanceKeys =
                  query.filter().processInstanceKeyOperations().stream()
                      .map(Operation::values)
                      .flatMap(List::stream)
                      .toList();

              final var incidentItems = new ArrayList<IncidentEntity>();
              for (final var key : processInstanceKeys) {
                // Create 20 incidents for each process instance key
                for (int i = 0; i < 50; i++) {
                  final long incidentKey = key * 100 + i;
                  incidentItems.add(fakeIncidentEntity(incidentKey, key));
                }
              }

              return new SearchQueryResult.Builder<IncidentEntity>()
                  .items(incidentItems)
                  .total(numItems)
                  .endCursor(Long.toString(incidentItems.getLast().incidentKey()))
                  .build();
            });
  }
}
