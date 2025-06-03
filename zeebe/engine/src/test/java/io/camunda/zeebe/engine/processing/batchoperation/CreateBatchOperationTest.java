/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationChunkIntent;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.List;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;
import org.mockito.Mockito;

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
                    mockProcessInstanceEntity(1L),
                    mockProcessInstanceEntity(2L),
                    mockProcessInstanceEntity(3L)))
            .total(3)
            .build();

    when(searchClientsProxy.searchProcessInstances(Mockito.any(ProcessInstanceQuery.class)))
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
}
