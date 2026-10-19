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
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.BatchOperationChunkIntent;
import io.camunda.zeebe.protocol.record.intent.BatchOperationExecutionIntent;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Restarts the engine while a batch operation has chunks persisted but nothing executed yet, and
 * checks that the records written afterwards carry the batch operation's ordinal and the chunk's
 * items ordinal rebuilt from state. The restart boundary is deterministic: the search delivers one
 * page and fails on the next, and the query retry delay is long enough that initialization stays
 * stalled until the restart, so no EXECUTE command exists before it.
 */
public final class BatchOperationStorageOrdinalRestartTest {

  private static final int FIXED_ORDINAL = 1234;
  private static final int FIRST_PAGE_ITEMS = 100;
  private static final int LAST_PAGE_ITEMS = 100;

  private static final SearchClientsProxy SEARCH_CLIENTS_PROXY =
      Mockito.mock(SearchClientsProxy.class);
  private static final BrokerRequestAuthorizationConverter BROKER_REQUEST_AUTHORIZATION_CONVERTER =
      Mockito.mock(BrokerRequestAuthorizationConverter.class);

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .withEngineConfig(
              config ->
                  config
                      .setArchiverlessEnabled(true)
                      .setFixedStorageOrdinal(FIXED_ORDINAL)
                      .setBatchOperationQueryRetryMax(10)
                      .setBatchOperationQueryRetryInitialDelay(Duration.ofMinutes(5))
                      .setBatchOperationQueryRetryMaxDelay(Duration.ofMinutes(5)))
          .withSearchClientsProxy(SEARCH_CLIENTS_PROXY)
          .withBrokerRequestAuthorizationConverter(BROKER_REQUEST_AUTHORIZATION_CONVERTER);

  @Before
  public void setUp() {
    Mockito.reset(SEARCH_CLIENTS_PROXY);
    when(SEARCH_CLIENTS_PROXY.withSecurityContext(any())).thenReturn(SEARCH_CLIENTS_PROXY);
  }

  @Test
  public void shouldKeepOrdinalsAfterRestart() {
    // given the first page is chunked and the next page fails, so initialization stalls with
    // chunks in state and no execution until the query is retried minutes later
    when(SEARCH_CLIENTS_PROXY.searchProcessInstances(any(ProcessInstanceQuery.class)))
        .thenReturn(
            page(1, FIRST_PAGE_ITEMS, EngineConfiguration.DEFAULT_BATCH_OPERATION_QUERY_PAGE_SIZE))
        .thenThrow(new RuntimeException("search unavailable"));
    final long batchOperationKey =
        engine
            .batchOperation()
            .newCreation(BatchOperationType.CANCEL_PROCESS_INSTANCE)
            .withFilter(
                new UnsafeBuffer(
                    MsgPackConverter.convertToMsgPack(
                        new ProcessInstanceFilter.Builder().processInstanceKeys(1L).build())))
            .create()
            .getValue()
            .getBatchOperationKey();
    RecordingExporter.batchOperationChunkRecords()
        .withBatchOperationKey(batchOperationKey)
        .withIntent(BatchOperationChunkIntent.CREATED)
        .await();
    final long boundary =
        RecordingExporter.batchOperationInitializationRecords()
            .withBatchOperationKey(batchOperationKey)
            .withIntent(BatchOperationIntent.INITIALIZING)
            .getFirst()
            .getPosition();

    // when the engine restarts and the search now delivers the last page
    engine.stop();
    Mockito.reset(SEARCH_CLIENTS_PROXY);
    when(SEARCH_CLIENTS_PROXY.withSecurityContext(any())).thenReturn(SEARCH_CLIENTS_PROXY);
    when(SEARCH_CLIENTS_PROXY.searchProcessInstances(any(ProcessInstanceQuery.class)))
        .thenReturn(page(FIRST_PAGE_ITEMS + 1, LAST_PAGE_ITEMS, LAST_PAGE_ITEMS));
    engine.start();

    // then every execution record is written after the boundary, carries both ordinals and
    // covers the items of the chunk persisted before the restart
    final var executionRecords =
        RecordingExporter.batchOperationExecutionRecords()
            .withBatchOperationKey(batchOperationKey)
            .limit(
                record ->
                    record.getIntent() == BatchOperationExecutionIntent.EXECUTED
                        && record.getValue().getItemKeys().isEmpty())
            .collect(Collectors.toList());

    assertThat(executionRecords)
        .extracting(Record::getIntent)
        .contains(BatchOperationExecutionIntent.EXECUTING, BatchOperationExecutionIntent.EXECUTED);
    assertThat(executionRecords)
        .allSatisfy(record -> assertThat(record.getPosition()).isGreaterThan(boundary));
    assertThat(executionRecords)
        .extracting(record -> record.getValue().getStorageOrdinal())
        .containsOnly(FIXED_ORDINAL);
    assertThat(executionRecords)
        .extracting(record -> record.getValue().getBatchItemsStorageOrdinal())
        .containsOnly(0);
    assertThat(
            executionRecords.stream()
                .filter(record -> record.getIntent() == BatchOperationExecutionIntent.EXECUTED)
                .flatMap(record -> record.getValue().getItemKeys().stream())
                .collect(Collectors.toSet()))
        .containsAll(
            LongStream.rangeClosed(1, FIRST_PAGE_ITEMS + LAST_PAGE_ITEMS).boxed().toList());

    final var completed =
        RecordingExporter.batchOperationLifecycleRecords()
            .withBatchOperationKey(batchOperationKey)
            .withIntent(BatchOperationIntent.COMPLETED)
            .getFirst();
    assertThat(completed.getPosition()).isGreaterThan(boundary);
    assertThat(completed.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
  }

  /** A page of consecutive process instance keys, the total decides whether it is the last one. */
  private static SearchQueryResult<ProcessInstanceEntity> page(
      final long firstKey, final int items, final long total) {
    return new SearchQueryResult.Builder<ProcessInstanceEntity>()
        .items(
            LongStream.range(firstKey, firstKey + items)
                .mapToObj(BatchOperationStorageOrdinalRestartTest::fakeProcessInstanceEntity)
                .toList())
        .total(total)
        .build();
  }

  private static ProcessInstanceEntity fakeProcessInstanceEntity(final long processInstanceKey) {
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
