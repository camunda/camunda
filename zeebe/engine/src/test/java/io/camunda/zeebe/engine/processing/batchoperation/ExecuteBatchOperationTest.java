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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationExecutionIntent;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.mockito.Mockito;

public final class ExecuteBatchOperationTest extends AbstractBatchOperationTest {

  @Test
  public void shouldExecuteBatchOperationForCancelProcessInstance() {
    // given
    final var processInstanceKeys = Set.of(1L, 2L, 3L);
    final Map<String, Object> claims = Map.of("claim1", "value1", "claim2", "value2");
    final var batchOperationKey =
        createNewCancelProcessInstanceBatchOperation(processInstanceKeys, claims);

    // then we have a EXECUTED event and a and a follow-up command to execute again
    assertThat(
            RecordingExporter.batchOperationExecutionRecords()
                .withBatchOperationKey(batchOperationKey)
                .limitByCount(r -> r.getIntent() == BatchOperationExecutionIntent.EXECUTE, 2))
        .extracting(Record::getIntent)
        .containsSequence(
            BatchOperationExecutionIntent.EXECUTE,
            BatchOperationExecutionIntent.EXECUTING,
            BatchOperationExecutionIntent.EXECUTED,
            BatchOperationExecutionIntent.EXECUTE);

    // then we have completed event
    assertThat(
            RecordingExporter.batchOperationLifecycleRecords()
                .withBatchOperationKey(batchOperationKey)
                .onlyEvents()
                .limit(r -> r.getIntent() == BatchOperationIntent.COMPLETED))
        .extracting(Record::getIntent)
        .containsSequence(BatchOperationIntent.COMPLETED);

    // and we have several cancel process commands
    processInstanceKeys.forEach(
        key -> {
          final var cancelCommand =
              RecordingExporter.processInstanceRecords()
                  .withRecordType(RecordType.COMMAND)
                  .withProcessInstanceKey(key)
                  .getFirst();
          assertThat(cancelCommand.getIntent()).isEqualTo(ProcessInstanceIntent.CANCEL);
          assertThat(cancelCommand.getAuthorizations()).isEqualTo(claims);
        });
  }

  @Test
  public void shouldFailBatchOperationWhenRetriesAreExhausted() {
    // given
    when(searchClientsProxy.searchProcessInstances(Mockito.any(ProcessInstanceQuery.class)))
        .thenThrow(new RuntimeException("error"));

    final var filterBuffer =
        convertToBuffer(
            new ProcessInstanceFilter.Builder().processInstanceKeys(1L, 3L, 8L).build());

    final var batchOperationKey =
        engine
            .batchOperation()
            .newCreation(BatchOperationType.CANCEL_PROCESS_INSTANCE)
            .withFilter(filterBuffer)
            .create(DEFAULT_USER.getUsername())
            .getValue()
            .getBatchOperationKey();

    // then we have failed event
    assertThat(
            RecordingExporter.batchOperationPartitionLifecycleRecords(
                    BatchOperationIntent.FAIL_PARTITION)
                .withBatchOperationKey(batchOperationKey)
                .exists())
        .isTrue();

    assertThat(
            RecordingExporter.batchOperationLifecycleRecords(BatchOperationIntent.FAILED)
                .withBatchOperationKey(batchOperationKey)
                .exists())
        .isTrue();

    verify(searchClientsProxy, times(3)).searchProcessInstances(any());
  }

  @Test
  public void shouldDoNothingIfThereIsNoBatchOperation() {
    // given
    final var batchOperationKey = 1L;

    // when
    engine
        .batchOperation()
        .newExecution()
        .withBatchOperationKey(batchOperationKey)
        .expectRejection()
        .execute();

    // then
    assertThat(
            RecordingExporter.batchOperationExecutionRecords(BatchOperationExecutionIntent.EXECUTE)
                .withBatchOperationKey(batchOperationKey)
                .onlyCommandRejections()
                .getFirst())
        .extracting(Record::getRejectionType, Record::getRejectionReason)
        .containsOnly(
            RejectionType.NOT_FOUND,
            "No batch operation found for key '%d'.".formatted(batchOperationKey));
  }
}
