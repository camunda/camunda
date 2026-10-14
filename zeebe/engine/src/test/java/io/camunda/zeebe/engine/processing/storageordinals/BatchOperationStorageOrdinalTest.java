/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.storageordinals;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.stream.Collectors;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Rule;
import org.junit.Test;

public final class BatchOperationStorageOrdinalTest {

  private static final int PARTITION_COUNT = 2;
  private static final int FIXED_ORDINAL = 1234;

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Rule
  public final EngineRule engine =
      EngineRule.multiplePartition(PARTITION_COUNT)
          .withEngineConfig(
              config ->
                  config
                      .setArchiverlessEnabled(true)
                      .setFixedStorageOrdinal(FIXED_ORDINAL)
                      .setBatchOperationSchedulerInterval(Duration.ofDays(1)));

  @Test
  public void shouldAssignConfiguredOrdinalToBatchOperationCreatedRecord() {
    // given
    // nothing

    // when
    final var response =
        engine
            .batchOperation()
            .newCreation(BatchOperationType.CANCEL_PROCESS_INSTANCE)
            .withFilter(new UnsafeBuffer("{\"hasIncident\": false}".getBytes()))
            .create();
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
    // nothing

    // when
    final long batchOperationKey =
        engine
            .batchOperation()
            .newCreation(BatchOperationType.CANCEL_PROCESS_INSTANCE)
            .withFilter(new UnsafeBuffer("{\"hasIncident\": false}".getBytes()))
            .create()
            .getValue()
            .getBatchOperationKey();

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
}
