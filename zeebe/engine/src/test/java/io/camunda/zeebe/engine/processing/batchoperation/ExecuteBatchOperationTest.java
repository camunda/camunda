/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.SearchClientsProxy;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.BatchOperationExecutionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Set;
import java.util.stream.Collectors;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

public final class ExecuteBatchOperationTest {

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();
  private final SearchClientsProxy searchClientsProxy = Mockito.mock(SearchClientsProxy.class);

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition().withSearchClientsProxy(searchClientsProxy);

  @Test
  public void shouldExecuteBatchOperationForProcessInstanceCancellation() {
    // given
    final var processInstanceKeys = Set.of(1L, 2L, 3L);
    final var batchOperationKey =
        createNewProcessInstanceCancellationBatchOperation(processInstanceKeys);

    // then we have executed and completed event
    assertThat(
            RecordingExporter.batchOperationExecutionRecords()
                .withBatchOperationKey(batchOperationKey)
                .onlyEvents())
        .extracting(Record::getIntent)
        .containsSequence(
            BatchOperationExecutionIntent.EXECUTED, BatchOperationExecutionIntent.COMPLETED);

    // and a follow op up command to execute again
    assertThat(
            RecordingExporter.batchOperationExecutionRecords()
                .withBatchOperationKey(batchOperationKey)
                .onlyCommands())
        .extracting(Record::getIntent)
        .containsSequence(BatchOperationExecutionIntent.EXECUTE);

    // and we have several process cancellation commands
    processInstanceKeys.forEach(
        key -> {
          assertThat(RecordingExporter.processInstanceRecords().withProcessInstanceKey(key))
              .extracting(Record::getIntent)
              .containsSequence(ProcessInstanceIntent.CANCEL);
        });
  }

  @Test
  public void shouldDoNothingIfThereIsNoBatchOperation() {
    // given
    final var batchOperationKey = 1L;

    // when
    engine
        .batchOperation()
        .newExecution(BatchOperationType.PROCESS_CANCELLATION)
        .withBatchOperationKey(batchOperationKey)
        .createCanceled();

    // then
    assertThat(
            RecordingExporter.batchOperationExecutionRecords()
                .withBatchOperationKey(batchOperationKey))
        .extracting(Record::getIntent)
        .doesNotContain(
            BatchOperationExecutionIntent.EXECUTED, BatchOperationExecutionIntent.COMPLETED);
  }

  private long createNewProcessInstanceCancellationBatchOperation(final Set<Long> itemKeys) {
    final var result =
        new SearchQueryResult.Builder<ProcessInstanceEntity>()
            .items(
                itemKeys.stream().map(this::mockProcessInstanceEntity).collect(Collectors.toList()))
            .total(itemKeys.size())
            .build();
    Mockito.when(searchClientsProxy.searchProcessInstances(Mockito.any(ProcessInstanceQuery.class)))
        .thenReturn(result);

    final var filterBuffer =
        convertToBuffer(
            new ProcessInstanceFilter.Builder().processInstanceKeys(1L, 3L, 8L).build());

    return engine
        .batchOperation()
        .newCreation(BatchOperationType.PROCESS_CANCELLATION)
        .withFilter(filterBuffer)
        .create()
        .getValue()
        .getBatchOperationKey();
  }

  private static UnsafeBuffer convertToBuffer(final Object object) {
    return new UnsafeBuffer(MsgPackConverter.convertToMsgPack(object));
  }

  private ProcessInstanceEntity mockProcessInstanceEntity(final long processInstanceKey) {
    final var entity = mock(ProcessInstanceEntity.class);
    when(entity.processInstanceKey()).thenReturn(processInstanceKey);
    return entity;
  }
}
