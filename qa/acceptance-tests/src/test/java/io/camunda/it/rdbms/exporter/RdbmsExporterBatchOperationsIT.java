/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.exporter;

import static io.camunda.it.rdbms.exporter.RecordFixtures.getBatchOperationChunkRecord;
import static io.camunda.it.rdbms.exporter.RecordFixtures.getBatchOperationCreatedRecord;
import static io.camunda.it.rdbms.exporter.RecordFixtures.getBatchOperationExecutionCompletedRecord;
import static io.camunda.it.rdbms.exporter.RecordFixtures.getBatchOperationLifecycleCanceledRecord;
import static io.camunda.it.rdbms.exporter.RecordFixtures.getBatchOperationLifecyclePausedRecord;
import static io.camunda.it.rdbms.exporter.RecordFixtures.getBatchOperationProcessCancelledRecord;
import static io.camunda.it.rdbms.exporter.RecordFixtures.getFailedBatchOperationProcessCancelledRecord;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.exporter.rdbms.RdbmsExporterWrapper;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationItemState;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationState;
import io.camunda.zeebe.broker.exporter.context.ExporterConfiguration;
import io.camunda.zeebe.broker.exporter.context.ExporterContext;
import io.camunda.zeebe.exporter.test.ExporterTestController;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@Tag("rdbms")
@SpringBootTest(classes = {RdbmsTestConfiguration.class})
@TestPropertySource(
    properties = {
      "spring.liquibase.enabled=false",
      "camunda.database.type=rdbms",
      "zeebe.broker.exporters.rdbms.args.maxQueueSize=0",
      "camunda.database.index-prefix=C8_"
    })
class RdbmsExporterBatchOperationsIT {

  private final ExporterTestController controller = new ExporterTestController();

  @Autowired private RdbmsService rdbmsService;

  private RdbmsExporterWrapper exporter;

  @BeforeEach
  void setUp() {
    exporter = new RdbmsExporterWrapper(rdbmsService);
    exporter.configure(
        new ExporterContext(
            null,
            new ExporterConfiguration("foo", Map.of("maxQueueSize", 0)),
            1,
            Mockito.mock(MeterRegistry.class, Mockito.RETURNS_DEEP_STUBS),
            null));
    exporter.open(controller);
  }

  @Test
  public void shouldExportBatchOperation() {
    // given
    final var batchOperationCreatedRecord = getBatchOperationCreatedRecord(1L);
    final var batchOperationKey = batchOperationCreatedRecord.getKey();
    final var batchOperationChunkRecord = getBatchOperationChunkRecord(batchOperationKey, 2L);
    final var batchOperationExecutionCompletedRecord =
        getBatchOperationExecutionCompletedRecord(batchOperationKey, 3L);

    // when
    exporter.export(batchOperationCreatedRecord);
    exporter.export(batchOperationChunkRecord);

    // then
    var batchOperation = rdbmsService.getBatchOperationReader().findOne(batchOperationKey).get();
    assertThat(batchOperation).isNotNull();
    assertThat(batchOperation.operationsTotalCount()).isEqualTo(3);
    assertThat(batchOperation.state()).isEqualTo(BatchOperationState.ACTIVE);

    // and when we complete it
    exporter.export(batchOperationExecutionCompletedRecord);

    // then it should be completed
    batchOperation = rdbmsService.getBatchOperationReader().findOne(batchOperationKey).get();
    assertThat(batchOperation.state()).isEqualTo(BatchOperationState.COMPLETED);
  }

  @Test
  public void shouldCancelBatchOperation() {
    // given
    final var batchOperationCreatedRecord = getBatchOperationCreatedRecord(1L);
    final var batchOperationKey = batchOperationCreatedRecord.getKey();
    final var batchOperationChunkRecord = getBatchOperationChunkRecord(batchOperationKey, 2L);
    final var batchOperationCanceledRecord =
        getBatchOperationLifecycleCanceledRecord(batchOperationKey, 3L);

    // when
    exporter.export(batchOperationCreatedRecord);
    exporter.export(batchOperationChunkRecord);

    // then
    var batchOperation = rdbmsService.getBatchOperationReader().findOne(batchOperationKey).get();
    assertThat(batchOperation).isNotNull();
    assertThat(batchOperation.operationsTotalCount()).isEqualTo(3);
    assertThat(batchOperation.state()).isEqualTo(BatchOperationState.ACTIVE);

    // and when we cancel it
    exporter.export(batchOperationCanceledRecord);

    // then it should be canceled
    batchOperation = rdbmsService.getBatchOperationReader().findOne(batchOperationKey).get();
    assertThat(batchOperation.state()).isEqualTo(BatchOperationState.CANCELED);

    // and the items should be canceled
    final var batchOperationItems =
        rdbmsService.getBatchOperationReader().getItems(batchOperationKey);
    assertThat(batchOperationItems).hasSize(3);
    assertThat(
            batchOperationItems.stream()
                .allMatch(item -> item.state() == BatchOperationItemState.CANCELED))
        .isTrue();
  }

  @Test
  public void shouldPauseBatchOperation() {
    // given
    final var batchOperationCreatedRecord = getBatchOperationCreatedRecord(1L);
    final var batchOperationKey = batchOperationCreatedRecord.getKey();
    final var batchOperationChunkRecord = getBatchOperationChunkRecord(batchOperationKey, 2L);
    final var batchOperationPauseRecord =
        getBatchOperationLifecyclePausedRecord(batchOperationKey, 3L);

    exporter.export(batchOperationCreatedRecord);
    exporter.export(batchOperationChunkRecord);

    // when we pause it
    exporter.export(batchOperationPauseRecord);

    // then it should be canceled
    final var batchOperation =
        rdbmsService.getBatchOperationReader().findOne(batchOperationKey).get();
    assertThat(batchOperation.state()).isEqualTo(BatchOperationState.PAUSED);
  }

  @Test
  public void shouldMonitorProcessInstanceCancellationBatchOperation() {
    // given
    final var batchOperationCreatedRecord = getBatchOperationCreatedRecord(1L);
    final var batchOperationKey = batchOperationCreatedRecord.getKey();
    final var batchOperationChunkRecord = getBatchOperationChunkRecord(batchOperationKey, 2L);
    final var processInstanceKey = 1L;
    final var processCancelledRecord =
        getBatchOperationProcessCancelledRecord(processInstanceKey, batchOperationKey, 3L);

    // when
    exporter.export(batchOperationCreatedRecord);
    exporter.export(batchOperationChunkRecord);
    exporter.export(processCancelledRecord);

    // then
    final var batchOperation =
        rdbmsService.getBatchOperationReader().findOne(batchOperationKey).get();
    assertThat(batchOperation).isNotNull();
    assertThat(batchOperation.operationsTotalCount()).isEqualTo(3);
    assertThat(batchOperation.operationsCompletedCount()).isEqualTo(1);
    assertThat(batchOperation.operationsFailedCount()).isEqualTo(0);
    assertThat(batchOperation.state()).isEqualTo(BatchOperationState.ACTIVE);
  }

  @Test
  public void shouldMonitorFailedProcessInstanceCancellationBatchOperation() {
    // given
    final var batchOperationCreatedRecord = getBatchOperationCreatedRecord(1L);
    final var batchOperationKey = batchOperationCreatedRecord.getKey();
    final var batchOperationChunkRecord = getBatchOperationChunkRecord(batchOperationKey, 2L);
    final var processInstanceKey = 1L;
    final var processFailedCancelledRecord =
        getFailedBatchOperationProcessCancelledRecord(processInstanceKey, batchOperationKey, 3L);

    // when
    exporter.export(batchOperationCreatedRecord);
    exporter.export(batchOperationChunkRecord);
    exporter.export(processFailedCancelledRecord);

    // then
    final var batchOperation =
        rdbmsService.getBatchOperationReader().findOne(batchOperationKey).get();
    assertThat(batchOperation).isNotNull();
    assertThat(batchOperation.operationsTotalCount()).isEqualTo(3);
    assertThat(batchOperation.operationsCompletedCount()).isEqualTo(0);
    assertThat(batchOperation.operationsFailedCount()).isEqualTo(1);
    assertThat(batchOperation.state()).isEqualTo(BatchOperationState.ACTIVE);
  }
}
