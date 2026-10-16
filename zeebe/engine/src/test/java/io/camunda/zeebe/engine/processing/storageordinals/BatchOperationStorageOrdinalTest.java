/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.storageordinals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.SearchClientsProxy;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.api.model.config.initialization.ConfiguredUser;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationExecutionIntent;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationCreationRecordValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

public final class BatchOperationStorageOrdinalTest {

  private static final int PARTITION_COUNT = 2;
  private static final int FIXED_ORDINAL = 1234;

  private static final ConfiguredUser DEFAULT_USER =
      new ConfiguredUser(
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString());
  private static final ConfiguredUser USER_WITHOUT_PERMISSIONS =
      new ConfiguredUser(
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString());

  private final SearchClientsProxy searchClientsProxy = Mockito.mock(SearchClientsProxy.class);
  private final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter =
      Mockito.mock(BrokerRequestAuthorizationConverter.class);

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Rule
  public final EngineRule engine =
      EngineRule.multiplePartition(PARTITION_COUNT)
          .withIdentitySetup()
          .withAuthorizationsEnabled(true)
          .withSecurityConfig(
              cfg ->
                  cfg.getInitialization().setUsers(List.of(DEFAULT_USER, USER_WITHOUT_PERMISSIONS)))
          .withSecurityConfig(
              cfg -> {
                final var defaultRoles = new HashMap<>(cfg.getInitialization().getDefaultRoles());
                defaultRoles.put("admin", Map.of("users", List.of(DEFAULT_USER.getUsername())));
                cfg.getInitialization().setDefaultRoles(defaultRoles);
              })
          .withEngineConfig(
              config ->
                  config
                      .setArchiverlessEnabled(true)
                      .setFixedStorageOrdinal(FIXED_ORDINAL)
                      .setBatchOperationQueryRetryMax(10)
                      .setBatchOperationQueryRetryInitialDelay(Duration.ofMinutes(5))
                      .setBatchOperationQueryRetryMaxDelay(Duration.ofMinutes(5)))
          .withSearchClientsProxy(searchClientsProxy)
          .withBrokerRequestAuthorizationConverter(brokerRequestAuthorizationConverter);

  @Before
  public void setUp() {
    when(searchClientsProxy.withSecurityContext(any())).thenReturn(searchClientsProxy);
  }

  @Test
  public void shouldAssignConfiguredOrdinalToBatchOperationCreatedRecord() {
    // given
    stubProcessInstanceSearch(Set.of());

    // when
    final var response = createBatchOperationRecord();
    final long batchOperationKey = response.getValue().getBatchOperationKey();

    // then
    assertThat(response.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);

    final var created =
        RecordingExporter.batchOperationCreationRecords()
            .withBatchOperationKey(batchOperationKey)
            .withPartitionId(1)
            .withIntent(BatchOperationIntent.CREATED)
            .getFirst();
    assertThat(created.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
  }

  @Test
  public void shouldCarryOrdinalToDistributedBatchOperationCreatedRecords() {
    // given
    stubProcessInstanceSearch(Set.of());

    // when
    final long batchOperationKey = createBatchOperation();

    // then
    assertThat(
            RecordingExporter.batchOperationCreationRecords()
                .withBatchOperationKey(batchOperationKey)
                .withPartitionId(2)
                .limit(record -> record.getIntent() == BatchOperationIntent.CREATED)
                .collect(Collectors.toList()))
        .hasSize(2)
        .extracting(record -> record.getValue().getStorageOrdinal())
        .containsOnly(FIXED_ORDINAL);
  }

  @Test
  public void shouldCarryOrdinalToInitializationRecords() {
    // given
    stubProcessInstanceSearch(Set.of(1L, 2L, 3L));

    // when
    final long batchOperationKey = createBatchOperation();

    // then
    for (int partitionId = 1; partitionId <= PARTITION_COUNT; partitionId++) {
      final var records =
          RecordingExporter.batchOperationInitializationRecords()
              .withBatchOperationKey(batchOperationKey)
              .withPartitionId(partitionId)
              .limit(record -> record.getIntent() == BatchOperationIntent.INITIALIZED)
              .collect(Collectors.toList());

      assertThat(records).extracting(Record::getIntent).contains(BatchOperationIntent.INITIALIZED);
      assertThat(records)
          .extracting(record -> record.getValue().getStorageOrdinal())
          .containsOnly(FIXED_ORDINAL);
    }
  }

  @Test
  public void shouldCarryOrdinalToInitializingRecord() {
    // given
    when(searchClientsProxy.searchProcessInstances(any(ProcessInstanceQuery.class)))
        .thenReturn(processInstancePage(Set.of(1L, 2L, 3L), 10000))
        .thenThrow(new RuntimeException("search unavailable"));

    // when
    final long batchOperationKey = createBatchOperation();

    // then
    final var initializing =
        RecordingExporter.batchOperationInitializationRecords()
            .withBatchOperationKey(batchOperationKey)
            .withIntent(BatchOperationIntent.INITIALIZING)
            .getFirst();
    assertThat(initializing.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
  }

  @Test
  public void shouldCarryOrdinalToExecutionRecords() {
    // given
    stubProcessInstanceSearch(Set.of(1L, 2L, 3L));

    // when
    final long batchOperationKey = createBatchOperation();

    // then
    final var records =
        RecordingExporter.batchOperationExecutionRecords()
            .withBatchOperationKey(batchOperationKey)
            .withPartitionId(1)
            .limit(
                record ->
                    record.getIntent() == BatchOperationExecutionIntent.EXECUTED
                        && record.getValue().getItemKeys().isEmpty())
            .collect(Collectors.toList());

    assertThat(records)
        .extracting(record -> record.getIntent())
        .contains(BatchOperationExecutionIntent.EXECUTING, BatchOperationExecutionIntent.EXECUTED);
    assertThat(records)
        .extracting(record -> record.getValue().getStorageOrdinal())
        .containsOnly(FIXED_ORDINAL);
  }

  @Test
  public void shouldCarryOrdinalToCompletionRecords() {
    // given
    stubProcessInstanceSearch(Set.of(1L, 2L, 3L));

    // when
    final long batchOperationKey = createBatchOperation();

    // then
    assertThat(
            RecordingExporter.batchOperationPartitionLifecycleRecords()
                .withBatchOperationKey(batchOperationKey)
                .withPartitionId(1)
                .limitByCount(
                    record -> record.getIntent() == BatchOperationIntent.PARTITION_COMPLETED,
                    PARTITION_COUNT)
                .collect(Collectors.toList()))
        .extracting(record -> record.getValue().getStorageOrdinal())
        .containsOnly(FIXED_ORDINAL);

    assertThat(
            RecordingExporter.batchOperationPartitionLifecycleRecords()
                .withBatchOperationKey(batchOperationKey)
                .withPartitionId(2)
                .limit(record -> record.getIntent() == BatchOperationIntent.PARTITION_COMPLETED)
                .collect(Collectors.toList()))
        .extracting(record -> record.getValue().getStorageOrdinal())
        .containsOnly(FIXED_ORDINAL);

    final var completed =
        RecordingExporter.batchOperationLifecycleRecords()
            .withBatchOperationKey(batchOperationKey)
            .withPartitionId(1)
            .withIntent(BatchOperationIntent.COMPLETED)
            .getFirst();
    assertThat(completed.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
  }

  @Test
  public void shouldCarryOrdinalToCanceledRecords() {
    // given
    stubProcessInstanceSearchFailure();
    final long batchOperationKey = createBatchOperation();

    // when
    final var response =
        engine.batchOperation().newLifecycle().withBatchOperationKey(batchOperationKey).cancel();

    // then
    assertThat(response.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
    final var canceled =
        RecordingExporter.batchOperationLifecycleRecords()
            .withBatchOperationKey(batchOperationKey)
            .withPartitionId(1)
            .withIntent(BatchOperationIntent.CANCELED)
            .getFirst();
    assertThat(canceled.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);

    assertThat(
            RecordingExporter.batchOperationLifecycleRecords()
                .withBatchOperationKey(batchOperationKey)
                .withPartitionId(2)
                .limit(record -> record.getIntent() == BatchOperationIntent.CANCELED)
                .collect(Collectors.toList()))
        .hasSize(2)
        .extracting(record -> record.getValue().getStorageOrdinal())
        .containsOnly(FIXED_ORDINAL);
  }

  @Test
  public void shouldCarryOrdinalToSuspendedAndResumedRecords() {
    // given
    stubProcessInstanceSearchFailure();
    final long batchOperationKey = createBatchOperation();

    // when
    final var suspendResponse =
        engine.batchOperation().newLifecycle().withBatchOperationKey(batchOperationKey).suspend();
    final var resumeResponse =
        engine.batchOperation().newLifecycle().withBatchOperationKey(batchOperationKey).resume();

    // then
    assertThat(suspendResponse.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
    assertThat(resumeResponse.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
    final var suspended =
        RecordingExporter.batchOperationLifecycleRecords()
            .withBatchOperationKey(batchOperationKey)
            .withPartitionId(1)
            .withIntent(BatchOperationIntent.SUSPENDED)
            .getFirst();
    assertThat(suspended.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);

    final var resumed =
        RecordingExporter.batchOperationLifecycleRecords()
            .withBatchOperationKey(batchOperationKey)
            .withPartitionId(1)
            .withIntent(BatchOperationIntent.RESUMED)
            .getFirst();
    assertThat(resumed.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);

    assertThat(
            RecordingExporter.batchOperationLifecycleRecords()
                .withBatchOperationKey(batchOperationKey)
                .withPartitionId(2)
                .limit(record -> record.getIntent() == BatchOperationIntent.SUSPENDED)
                .collect(Collectors.toList()))
        .hasSize(2)
        .extracting(record -> record.getValue().getStorageOrdinal())
        .containsOnly(FIXED_ORDINAL);

    assertThat(
            RecordingExporter.batchOperationLifecycleRecords()
                .withBatchOperationKey(batchOperationKey)
                .withPartitionId(2)
                .skipUntil(record -> record.getIntent() == BatchOperationIntent.SUSPENDED)
                .limit(record -> record.getIntent() == BatchOperationIntent.RESUMED)
                .collect(Collectors.toList()))
        .extracting(record -> record.getValue().getStorageOrdinal())
        .containsOnly(FIXED_ORDINAL);
  }

  @Test
  public void shouldCarryOrdinalToRejectedLifecycleCommand() {
    // given
    stubProcessInstanceSearchFailure();
    final long batchOperationKey = createBatchOperation();
    engine.batchOperation().newLifecycle().withBatchOperationKey(batchOperationKey).suspend();

    // when
    final var rejection =
        engine
            .batchOperation()
            .newLifecycle()
            .withBatchOperationKey(batchOperationKey)
            .expectRejection()
            .suspend();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
    assertThat(rejection.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
  }

  @Test
  public void shouldCarryOrdinalToForbiddenLifecycleCommands() {
    // given
    stubProcessInstanceSearchFailure();
    final long batchOperationKey = createBatchOperation();
    final var lifecycle =
        engine.batchOperation().newLifecycle().withBatchOperationKey(batchOperationKey);

    // when
    final var forbiddenCancel =
        lifecycle.expectRejection().cancel(USER_WITHOUT_PERMISSIONS.getUsername());
    final var forbiddenSuspend =
        lifecycle.expectRejection().suspend(USER_WITHOUT_PERMISSIONS.getUsername());
    final var forbiddenResume =
        lifecycle.expectRejection().resume(USER_WITHOUT_PERMISSIONS.getUsername());

    // then
    assertThat(List.of(forbiddenCancel, forbiddenSuspend, forbiddenResume))
        .allSatisfy(
            rejection -> {
              assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.FORBIDDEN);
              assertThat(rejection.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
            });
  }

  @Test
  public void shouldCarryOrdinalToFailedRecords() {
    // given
    stubProcessInstanceSearchFailure();
    final long batchOperationKey = createBatchOperation();

    // when
    final var followerFailed =
        engine
            .batchOperation()
            .newPartitionLifecycle()
            .withBatchOperationKey(batchOperationKey)
            .onPartition(2)
            .fail();
    final var leadFailed =
        engine
            .batchOperation()
            .newPartitionLifecycle()
            .withBatchOperationKey(batchOperationKey)
            .onPartition(1)
            .fail();

    // then
    assertThat(followerFailed.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
    assertThat(leadFailed.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);

    assertThat(
            RecordingExporter.batchOperationPartitionLifecycleRecords()
                .withBatchOperationKey(batchOperationKey)
                .withPartitionId(1)
                .onlyEvents()
                .limitByCount(
                    record -> record.getIntent() == BatchOperationIntent.PARTITION_FAILED,
                    PARTITION_COUNT)
                .collect(Collectors.toList()))
        .extracting(record -> record.getValue().getStorageOrdinal())
        .containsOnly(FIXED_ORDINAL);

    final var failed =
        RecordingExporter.batchOperationLifecycleRecords()
            .withBatchOperationKey(batchOperationKey)
            .withPartitionId(1)
            .withIntent(BatchOperationIntent.FAILED)
            .getFirst();
    assertThat(failed.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
  }

  private long createBatchOperation() {
    final long batchOperationKey = createBatchOperationRecord().getValue().getBatchOperationKey();
    for (int partitionId = 1; partitionId <= PARTITION_COUNT; partitionId++) {
      RecordingExporter.batchOperationCreationRecords()
          .withBatchOperationKey(batchOperationKey)
          .withPartitionId(partitionId)
          .withIntent(BatchOperationIntent.CREATED)
          .await();
    }
    return batchOperationKey;
  }

  private Record<BatchOperationCreationRecordValue> createBatchOperationRecord() {
    final var filter =
        new UnsafeBuffer(
            MsgPackConverter.convertToMsgPack(
                new ProcessInstanceFilter.Builder().processInstanceKeys(1L, 3L, 8L).build()));
    return engine
        .batchOperation()
        .newCreation(BatchOperationType.CANCEL_PROCESS_INSTANCE)
        .withFilter(filter)
        .create();
  }

  private void stubProcessInstanceSearch(final Set<Long> processInstanceKeys) {
    when(searchClientsProxy.searchProcessInstances(any(ProcessInstanceQuery.class)))
        .thenReturn(processInstancePage(processInstanceKeys, processInstanceKeys.size()));
  }

  private SearchQueryResult<ProcessInstanceEntity> processInstancePage(
      final Set<Long> processInstanceKeys, final long total) {
    return new SearchQueryResult.Builder<ProcessInstanceEntity>()
        .items(processInstanceKeys.stream().map(this::fakeProcessInstanceEntity).toList())
        .total(total)
        .build();
  }

  private void stubProcessInstanceSearchFailure() {
    when(searchClientsProxy.searchProcessInstances(any(ProcessInstanceQuery.class)))
        .thenThrow(new RuntimeException("search unavailable"));
  }

  private ProcessInstanceEntity fakeProcessInstanceEntity(final long processInstanceKey) {
    return new ProcessInstanceEntity(
        processInstanceKey,
        null, // rootProcessInstanceKey
        "fake-process-def-id", // processDefinitionId
        null, // processDefinitionName
        -1, // processDefinitionVersion
        null, // processDefinitionVersionTag
        -1L, // processDefinitionKey
        -1L, // parentProcessInstanceKey
        -1L, // parentFlowNodeInstanceKey
        OffsetDateTime.now(), // startDate
        null, // endDate
        ProcessInstanceEntity.ProcessInstanceState.ACTIVE, // state
        false, // hasIncident
        TenantOwned.DEFAULT_TENANT_IDENTIFIER,
        null, // treePath
        Set.of(), // tags
        null, // businessId
        null); // suspendedDate
  }
}
