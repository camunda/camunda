/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.service.ProcessInstanceServices;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

public final class CreateBatchOperationTest {

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  private final ProcessInstanceServices processInstanceService =
      Mockito.mock(ProcessInstanceServices.class);

  @Rule
  public final EngineRule ENGINE =
      EngineRule.singlePartition().withSearchQueryService(processInstanceService);

  @Before
  public void setup() {
    Mockito.when(processInstanceService.search(Mockito.any(ProcessInstanceQuery.class)))
        .thenReturn(new SearchQueryResult<>(0, List.of(), null, null));
  }

  @Test
  public void shouldRejectWithoutFilter() {
    // when
    final var batchOperationKey =
        ENGINE
            .batchOperation()
            .ofType(BatchOperationType.PROCESS_CANCELLATION)
            .expectRejection()
            .create();

    // then
    final var result =
        RecordingExporter.batchOperationCreationRecords()
            .withBatchOperationKey(batchOperationKey)
            .filter(r -> r.getRejectionType() != null)
            .getFirst();

    // TODO why is this working? Where does this NULL_VAL come from?
    assertThat(result.getRejectionType()).isEqualTo(RejectionType.NULL_VAL);
    assertThat(result.getIntent()).isEqualTo(BatchOperationIntent.CREATE);
  }

  @Test
  public void shouldScheduleSingleProcessInstanceCancel() {
    // given
    final var numberOfPi = 21;
    final var processInstances =
        mockProcessInstanceEntities(LongStream.rangeClosed(1, numberOfPi).boxed().toList());
    Mockito.when(processInstanceService.search(Mockito.any(ProcessInstanceQuery.class)))
        .thenReturn(new SearchQueryResult<>(1, processInstances, null, null));

    // when
    final long batchOperationKey =
        ENGINE
            .batchOperation()
            .ofType(BatchOperationType.PROCESS_CANCELLATION)
            .withFilter(new ProcessInstanceFilter.Builder().partitionIds(List.of(1)).build())
            .create();

    // then
    assertThat(
            RecordingExporter.batchOperationCreationRecords()
                .withBatchOperationKey(batchOperationKey))
        .extracting(Record::getIntent)
        .containsSequence(BatchOperationIntent.CREATED);
    assertThat(
            RecordingExporter.batchOperationExecutionRecords()
                .withBatchOperationKey(batchOperationKey))
        .extracting(Record::getIntent)
        .containsSequence(
            BatchOperationIntent.EXECUTE,
            BatchOperationIntent.EXECUTING,
            BatchOperationIntent.EXECUTED,
            BatchOperationIntent.EXECUTE,
            BatchOperationIntent.COMPLETED);

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.CANCEL)
                .filter(r -> r.getRejectionType() != RejectionType.NULL_VAL))
        .extracting(Record::getIntent)
        .hasSize(numberOfPi);
  }

  private List<ProcessInstanceEntity> mockProcessInstanceEntities(final List<Long> keys) {
    final List<ProcessInstanceEntity> result = new ArrayList<>();
    for (final long key : keys) {
      final var processInstanceEntity = Mockito.mock(ProcessInstanceEntity.class);
      Mockito.when(processInstanceEntity.processInstanceKey()).thenReturn(key);
      result.add(processInstanceEntity);
    }
    return result;
  }
}
