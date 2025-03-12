/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;

import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.service.ProcessInstanceServices;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.intent.CommandDistributionIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import io.camunda.zeebe.protocol.record.value.CommandDistributionRecordValue;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

public final class CreateBatchOperationMultiPartitionTest {
  private static final int PARTITION_COUNT = 3;

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  private final ProcessInstanceServices processInstanceService =
      Mockito.mock(ProcessInstanceServices.class);

  @Rule
  public final EngineRule ENGINE =
      EngineRule.multiplePartition(PARTITION_COUNT).withSearchQueryService(processInstanceService);

  @Before
  public void setup() {
    Mockito.when(processInstanceService.search(Mockito.any(ProcessInstanceQuery.class)))
        .thenReturn(new SearchQueryResult<>(0, List.of(), null, null));
  }

  @Test
  public void shouldScheduleOnAllPartitions() {
    // given
    final var pi = mockProcessInstanceEntity(1);
    Mockito.when(processInstanceService.search(Mockito.any(ProcessInstanceQuery.class)))
        .thenReturn(new SearchQueryResult<>(1, List.of(pi), null, null));

    // when
    final long batchOperationKey =
        ENGINE
            .batchOperation()
            .ofType(BatchOperationType.PROCESS_CANCELLATION)
            .withFilter(new ProcessInstanceFilter.Builder().partitionIds(List.of(1)).build())
            .create();

    assertThat(
            RecordingExporter.records()
                .withPartitionId(1)
                .limit(record -> record.getIntent().equals(CommandDistributionIntent.FINISHED))
                .filter(
                    record ->
                        record.getValueType() == ValueType.BATCH_OPERATION
                            || (record.getValueType() == ValueType.COMMAND_DISTRIBUTION
                                && ((CommandDistributionRecordValue) record.getValue()).getIntent()
                                    == BatchOperationIntent.CREATE)))
        .extracting(
            Record::getIntent,
            Record::getRecordType,
            r ->
                // We want to verify the partition id where the creation was distributing to and
                // where it was completed. Since only the CommandDistribution records have a
                // value that contains the partition id, we use the partition id the record was
                // written on for the other records.
                r.getValue() instanceof CommandDistributionRecordValue
                    ? ((CommandDistributionRecordValue) r.getValue()).getPartitionId()
                    : r.getPartitionId())
        .startsWith(
            tuple(BatchOperationIntent.CREATE, RecordType.COMMAND, 1),
            tuple(BatchOperationIntent.CREATED, RecordType.EVENT, 1),
            tuple(CommandDistributionIntent.STARTED, RecordType.EVENT, 1))
        .containsSubsequence(
            tuple(CommandDistributionIntent.DISTRIBUTING, RecordType.EVENT, 2),
            tuple(CommandDistributionIntent.ACKNOWLEDGE, RecordType.COMMAND, 2),
            tuple(CommandDistributionIntent.ACKNOWLEDGED, RecordType.EVENT, 2))
        .containsSubsequence(
            tuple(CommandDistributionIntent.DISTRIBUTING, RecordType.EVENT, 3),
            tuple(CommandDistributionIntent.ACKNOWLEDGE, RecordType.COMMAND, 3),
            tuple(CommandDistributionIntent.ACKNOWLEDGED, RecordType.EVENT, 3))
        .endsWith(tuple(CommandDistributionIntent.FINISHED, RecordType.EVENT, 1));
    for (int partitionId = 2; partitionId < PARTITION_COUNT; partitionId++) {
      assertThat(
              RecordingExporter.batchOperationCreationRecords()
                  .withBatchOperationKey(batchOperationKey)
                  .withPartitionId(partitionId)
                  .limit(record -> record.getIntent().equals(BatchOperationIntent.CREATED))
                  .collect(Collectors.toList()))
          .extracting(Record::getIntent)
          .containsExactly(BatchOperationIntent.CREATE, BatchOperationIntent.CREATED);
    }
  }

  private ProcessInstanceEntity mockProcessInstanceEntity(final long key) {
    final var processInstanceEntity = Mockito.mock(ProcessInstanceEntity.class);
    Mockito.when(processInstanceEntity.processInstanceKey()).thenReturn(key);
    return processInstanceEntity;
  }
}
