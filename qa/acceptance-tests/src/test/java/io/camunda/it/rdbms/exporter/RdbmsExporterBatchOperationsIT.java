/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.exporter;

import static io.camunda.it.rdbms.exporter.RecordFixtures.getBatchOperationChunkRecord;
import static io.camunda.it.rdbms.exporter.RecordFixtures.getBatchOperationCompletedRecord;
import static io.camunda.it.rdbms.exporter.RecordFixtures.getBatchOperationCompletedWithErrorsRecord;
import static io.camunda.it.rdbms.exporter.RecordFixtures.getBatchOperationCreatedRecord;
import static io.camunda.it.rdbms.exporter.RecordFixtures.getBatchOperationLifecycleCanceledRecord;
import static io.camunda.it.rdbms.exporter.RecordFixtures.getBatchOperationLifecycleResumeRecord;
import static io.camunda.it.rdbms.exporter.RecordFixtures.getBatchOperationLifecycleSuspendedRecord;
import static io.camunda.it.rdbms.exporter.RecordFixtures.getBatchOperationModifyProcessInstanceRecord;
import static io.camunda.it.rdbms.exporter.RecordFixtures.getBatchOperationProcessMigratedRecord;
import static io.camunda.it.rdbms.exporter.RecordFixtures.getBatchOperationResolveIncidentRecord;
import static io.camunda.it.rdbms.exporter.RecordFixtures.getCanceledProcessRecord;
import static io.camunda.it.rdbms.exporter.RecordFixtures.getFailedBatchOperationModifyProcessInstanceRecord;
import static io.camunda.it.rdbms.exporter.RecordFixtures.getFailedBatchOperationProcessMigratedRecord;
import static io.camunda.it.rdbms.exporter.RecordFixtures.getFailedBatchOperationResolveIncidentRecord;
import static io.camunda.it.rdbms.exporter.RecordFixtures.getRejectedCancelProcessRecord;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.exporter.rdbms.RdbmsExporterWrapper;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationItemEntity;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationItemState;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationState;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.zeebe.broker.exporter.context.ExporterConfiguration;
import io.camunda.zeebe.broker.exporter.context.ExporterContext;
import io.camunda.zeebe.exporter.test.ExporterTestController;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
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
      "zeebe.broker.exporters.rdbms.args.queueSize=0",
      "camunda.database.index-prefix=C8_"
    })
class RdbmsExporterBatchOperationsIT {

  private final ExporterTestController controller = new ExporterTestController();

  @Autowired private RdbmsService rdbmsService;

  private RdbmsExporterWrapper exporter;

  @BeforeEach
  void setUp() {
    setup(true);
  }

  private void setup(final boolean exportPendingItems) {
    exporter = new RdbmsExporterWrapper(rdbmsService);
    exporter.configure(
        new ExporterContext(
            null,
            new ExporterConfiguration(
                "foo",
                Map.of("queueSize", 0, "exportBatchOperationItemsOnCreation", exportPendingItems)),
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
    final var batchOperationCompletedRecord =
        getBatchOperationCompletedRecord(batchOperationKey, 3L);

    // when
    exporter.export(batchOperationCreatedRecord);
    exporter.export(batchOperationChunkRecord);

    // then
    var batchOperation =
        rdbmsService.getBatchOperationReader().findOne(String.valueOf(batchOperationKey)).get();
    assertThat(batchOperation).isNotNull();
    assertThat(batchOperation.operationsTotalCount()).isEqualTo(3);
    assertThat(batchOperation.state()).isEqualTo(BatchOperationState.ACTIVE);

    // and when we complete it
    exporter.export(batchOperationCompletedRecord);

    // then it should be completed
    batchOperation =
        rdbmsService.getBatchOperationReader().findOne(String.valueOf(batchOperationKey)).get();
    assertThat(batchOperation.state()).isEqualTo(BatchOperationState.COMPLETED);
  }

  @Test
  public void shouldExportBatchOperationWithErrors() {
    // given
    final var batchOperationCreatedRecord = getBatchOperationCreatedRecord(1L);
    final var batchOperationKey = batchOperationCreatedRecord.getKey();
    final var batchOperationChunkRecord = getBatchOperationChunkRecord(batchOperationKey, 2L);
    final var batchOperationCompletedRecord =
        getBatchOperationCompletedWithErrorsRecord(batchOperationKey, 3L);

    // when
    exporter.export(batchOperationCreatedRecord);
    exporter.export(batchOperationChunkRecord);

    // then
    var batchOperation =
        rdbmsService.getBatchOperationReader().findOne(String.valueOf(batchOperationKey)).get();
    assertThat(batchOperation).isNotNull();
    assertThat(batchOperation.operationsTotalCount()).isEqualTo(3);
    assertThat(batchOperation.state()).isEqualTo(BatchOperationState.ACTIVE);

    // and when we complete it
    exporter.export(batchOperationCompletedRecord);

    // then it should be completed
    batchOperation =
        rdbmsService.getBatchOperationReader().findOne(String.valueOf(batchOperationKey)).get();
    assertThat(batchOperation.state()).isEqualTo(BatchOperationState.PARTIALLY_COMPLETED);
    assertThat(batchOperation.errors()).isNotEmpty();
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
    var batchOperation =
        rdbmsService.getBatchOperationReader().findOne(String.valueOf(batchOperationKey)).get();
    assertThat(batchOperation).isNotNull();
    assertThat(batchOperation.operationsTotalCount()).isEqualTo(3);
    assertThat(batchOperation.state()).isEqualTo(BatchOperationState.ACTIVE);

    // and when we cancel it
    exporter.export(batchOperationCanceledRecord);

    // then it should be canceled
    batchOperation =
        rdbmsService.getBatchOperationReader().findOne(String.valueOf(batchOperationKey)).get();
    assertThat(batchOperation.state()).isEqualTo(BatchOperationState.CANCELED);

    // and the items should be canceled
    final var batchOperationItems = getItems(batchOperationKey);
    assertThat(batchOperationItems).hasSize(3);
    assertThat(
            batchOperationItems.stream()
                .allMatch(item -> item.state() == BatchOperationItemState.CANCELED))
        .isTrue();
  }

  @Test
  public void shouldSuspendBatchOperation() {
    // given
    final var batchOperationCreatedRecord = getBatchOperationCreatedRecord(1L);
    final var batchOperationKey = batchOperationCreatedRecord.getKey();
    final var batchOperationChunkRecord = getBatchOperationChunkRecord(batchOperationKey, 2L);
    final var batchOperationSuspendedRecord =
        getBatchOperationLifecycleSuspendedRecord(batchOperationKey, 3L);

    exporter.export(batchOperationCreatedRecord);
    exporter.export(batchOperationChunkRecord);

    // when we suspend it
    exporter.export(batchOperationSuspendedRecord);

    // then it should be canceled
    final var batchOperation =
        rdbmsService.getBatchOperationReader().findOne(String.valueOf(batchOperationKey)).get();
    assertThat(batchOperation.state()).isEqualTo(BatchOperationState.SUSPENDED);
  }

  @Test
  public void shouldResumeBatchOperation() {
    // given
    final var batchOperationCreatedRecord = getBatchOperationCreatedRecord(1L);
    final var batchOperationKey = batchOperationCreatedRecord.getKey();
    final var batchOperationChunkRecord = getBatchOperationChunkRecord(batchOperationKey, 2L);
    final var batchOperationSuspendedRecord =
        getBatchOperationLifecycleSuspendedRecord(batchOperationKey, 3L);
    final var batchOperationResumeRecord =
        getBatchOperationLifecycleResumeRecord(batchOperationKey, 4L);

    exporter.export(batchOperationCreatedRecord);
    exporter.export(batchOperationChunkRecord);
    exporter.export(batchOperationSuspendedRecord);

    // when we resume it
    exporter.export(batchOperationResumeRecord);

    // then it should be canceled
    final var batchOperation =
        rdbmsService.getBatchOperationReader().findOne(String.valueOf(batchOperationKey)).get();
    assertThat(batchOperation.state()).isEqualTo(BatchOperationState.ACTIVE);
  }

  @Test
  public void shouldMonitorCancelProcessInstanceBatchOperation() {
    // given
    final var batchOperationCreatedRecord = getBatchOperationCreatedRecord(1L);
    final var batchOperationKey = batchOperationCreatedRecord.getKey();
    final var batchOperationChunkRecord = getBatchOperationChunkRecord(batchOperationKey, 2L);
    final var processInstanceKey = 1L;
    final var canceledProcessRecord =
        getCanceledProcessRecord(processInstanceKey, batchOperationKey, 3L);

    // when
    exporter.export(batchOperationCreatedRecord);
    exporter.export(batchOperationChunkRecord);
    exporter.export(canceledProcessRecord);

    // then
    final var batchOperation =
        rdbmsService.getBatchOperationReader().findOne(String.valueOf(batchOperationKey)).get();
    assertThat(batchOperation).isNotNull();
    assertThat(batchOperation.operationsTotalCount()).isEqualTo(3);
    assertThat(batchOperation.operationsCompletedCount()).isEqualTo(1);
    assertThat(batchOperation.operationsFailedCount()).isEqualTo(0);
    assertThat(batchOperation.state()).isEqualTo(BatchOperationState.ACTIVE);
  }

  @Test
  public void shouldMonitorCancelProcessInstanceBatchOperationNoExportItemsOnCreation() {
    setup(false);
    // given
    final var batchOperationCreatedRecord = getBatchOperationCreatedRecord(1L);
    final var batchOperationKey = batchOperationCreatedRecord.getKey();
    final var batchOperationChunkRecord = getBatchOperationChunkRecord(batchOperationKey, 2L);
    final var processInstanceKey = 1L;
    final var canceledProcessRecord =
        getCanceledProcessRecord(processInstanceKey, batchOperationKey, 3L);

    // when
    exporter.export(batchOperationCreatedRecord);
    exporter.export(batchOperationChunkRecord);
    exporter.export(canceledProcessRecord);

    // then
    final var batchOperation =
        rdbmsService.getBatchOperationReader().findOne(String.valueOf(batchOperationKey)).get();
    assertThat(batchOperation).isNotNull();
    assertThat(batchOperation.operationsTotalCount()).isEqualTo(3);
    assertThat(batchOperation.operationsCompletedCount()).isEqualTo(1);
    assertThat(batchOperation.operationsFailedCount()).isEqualTo(0);
    assertThat(batchOperation.state()).isEqualTo(BatchOperationState.ACTIVE);

    // and the items should be completed
    final var batchOperationItems = getItems(batchOperationKey);
    assertThat(batchOperationItems).hasSize(1);
    assertThat(batchOperationItems.getFirst().state()).isEqualTo(BatchOperationItemState.COMPLETED);
  }

  @Test
  public void shouldMonitorFailedCancelProcessInstanceBatchOperation() {
    // given
    final var batchOperationCreatedRecord = getBatchOperationCreatedRecord(1L);
    final var batchOperationKey = batchOperationCreatedRecord.getKey();
    final var batchOperationChunkRecord = getBatchOperationChunkRecord(batchOperationKey, 2L);
    final var processInstanceKey = 1L;
    final var rejectedCancelProcessRecord =
        getRejectedCancelProcessRecord(processInstanceKey, batchOperationKey, 3L);

    // when
    exporter.export(batchOperationCreatedRecord);
    exporter.export(batchOperationChunkRecord);
    exporter.export(rejectedCancelProcessRecord);

    // then
    final var batchOperation =
        rdbmsService.getBatchOperationReader().findOne(String.valueOf(batchOperationKey)).get();
    assertThat(batchOperation).isNotNull();
    assertThat(batchOperation.operationsTotalCount()).isEqualTo(3);
    assertThat(batchOperation.operationsCompletedCount()).isEqualTo(0);
    assertThat(batchOperation.operationsFailedCount()).isEqualTo(1);
    assertThat(batchOperation.state()).isEqualTo(BatchOperationState.ACTIVE);
  }

  @Test
  public void shouldMonitorFailedCancelProcessInstanceBatchOperationNoExportItemsOnCreation() {
    setup(false);
    // given
    final var batchOperationCreatedRecord = getBatchOperationCreatedRecord(1L);
    final var batchOperationKey = batchOperationCreatedRecord.getKey();
    final var batchOperationChunkRecord = getBatchOperationChunkRecord(batchOperationKey, 2L);
    final var processInstanceKey = 1L;
    final var rejectedCancelProcessRecord =
        getRejectedCancelProcessRecord(processInstanceKey, batchOperationKey, 3L);

    // when
    exporter.export(batchOperationCreatedRecord);
    exporter.export(batchOperationChunkRecord);
    exporter.export(rejectedCancelProcessRecord);

    // then
    final var batchOperation =
        rdbmsService.getBatchOperationReader().findOne(String.valueOf(batchOperationKey)).get();
    assertThat(batchOperation).isNotNull();
    assertThat(batchOperation.operationsTotalCount()).isEqualTo(3);
    assertThat(batchOperation.operationsCompletedCount()).isEqualTo(0);
    assertThat(batchOperation.operationsFailedCount()).isEqualTo(1);
    assertThat(batchOperation.state()).isEqualTo(BatchOperationState.ACTIVE);

    // and the items should be completed
    final var batchOperationItems = getItems(batchOperationKey);
    assertThat(batchOperationItems).hasSize(1);
    assertThat(batchOperationItems.getFirst().state()).isEqualTo(BatchOperationItemState.FAILED);
  }

  @Test
  public void shouldMonitorResolveIncidentBatchOperation() {
    // given
    final var batchOperationCreatedRecord =
        getBatchOperationCreatedRecord(1L, BatchOperationType.RESOLVE_INCIDENT);
    final var batchOperationKey = batchOperationCreatedRecord.getKey();
    final var batchOperationChunkRecord = getBatchOperationChunkRecord(batchOperationKey, 2L);
    final var incidentKey = 1L;
    final var incidentResolvedRecord =
        getBatchOperationResolveIncidentRecord(incidentKey, batchOperationKey, 3L);

    // when
    exporter.export(batchOperationCreatedRecord);
    exporter.export(batchOperationChunkRecord);
    exporter.export(incidentResolvedRecord);

    // then
    final var batchOperation =
        rdbmsService.getBatchOperationReader().findOne(String.valueOf(batchOperationKey)).get();
    assertThat(batchOperation).isNotNull();
    assertThat(batchOperation.operationsTotalCount()).isEqualTo(3);
    assertThat(batchOperation.operationsCompletedCount()).isEqualTo(1);
    assertThat(batchOperation.operationsFailedCount()).isEqualTo(0);
    assertThat(batchOperation.state()).isEqualTo(BatchOperationState.ACTIVE);
  }

  @Test
  public void shouldMonitorFailedResolveIncidentBatchOperation() {
    // given
    final var batchOperationCreatedRecord =
        getBatchOperationCreatedRecord(1L, BatchOperationType.RESOLVE_INCIDENT);
    final var batchOperationKey = batchOperationCreatedRecord.getKey();
    final var batchOperationChunkRecord = getBatchOperationChunkRecord(batchOperationKey, 2L);
    final var incidentKey = 1L;
    final var resolveIncidentFailedRecord =
        getFailedBatchOperationResolveIncidentRecord(incidentKey, batchOperationKey, 3L);

    // when
    exporter.export(batchOperationCreatedRecord);
    exporter.export(batchOperationChunkRecord);
    exporter.export(resolveIncidentFailedRecord);

    // then
    final var batchOperation =
        rdbmsService.getBatchOperationReader().findOne(String.valueOf(batchOperationKey)).get();
    assertThat(batchOperation).isNotNull();
    assertThat(batchOperation.operationsTotalCount()).isEqualTo(3);
    assertThat(batchOperation.operationsCompletedCount()).isEqualTo(0);
    assertThat(batchOperation.operationsFailedCount()).isEqualTo(1);
    assertThat(batchOperation.state()).isEqualTo(BatchOperationState.ACTIVE);
  }

  @Test
  public void shouldMonitorProcessInstanceMigrationBatchOperation() {
    // given
    final var batchOperationCreatedRecord =
        getBatchOperationCreatedRecord(1L, BatchOperationType.MIGRATE_PROCESS_INSTANCE);
    final var batchOperationKey = batchOperationCreatedRecord.getKey();
    final var batchOperationChunkRecord = getBatchOperationChunkRecord(batchOperationKey, 2L);
    final var processInstanceKey = 1L;
    final var processMigratedRecord =
        getBatchOperationProcessMigratedRecord(processInstanceKey, batchOperationKey, 3L);

    // when
    exporter.export(batchOperationCreatedRecord);
    exporter.export(batchOperationChunkRecord);
    exporter.export(processMigratedRecord);

    // then
    final var batchOperation =
        rdbmsService.getBatchOperationReader().findOne(String.valueOf(batchOperationKey)).get();
    assertThat(batchOperation).isNotNull();
    assertThat(batchOperation.operationsTotalCount()).isEqualTo(3);
    assertThat(batchOperation.operationsCompletedCount()).isEqualTo(1);
    assertThat(batchOperation.operationsFailedCount()).isEqualTo(0);
    assertThat(batchOperation.state()).isEqualTo(BatchOperationState.ACTIVE);
  }

  @Test
  public void shouldMonitorFailedProcessInstanceMigrationBatchOperation() {
    // given
    final var batchOperationCreatedRecord =
        getBatchOperationCreatedRecord(1L, BatchOperationType.MIGRATE_PROCESS_INSTANCE);
    final var batchOperationKey = batchOperationCreatedRecord.getKey();
    final var batchOperationChunkRecord = getBatchOperationChunkRecord(batchOperationKey, 2L);
    final var processInstanceKey = 1L;
    final var processFailedMigrateRecord =
        getFailedBatchOperationProcessMigratedRecord(processInstanceKey, batchOperationKey, 3L);

    // when
    exporter.export(batchOperationCreatedRecord);
    exporter.export(batchOperationChunkRecord);
    exporter.export(processFailedMigrateRecord);

    // then
    final var batchOperation =
        rdbmsService.getBatchOperationReader().findOne(String.valueOf(batchOperationKey)).get();
    assertThat(batchOperation).isNotNull();
    assertThat(batchOperation.operationsTotalCount()).isEqualTo(3);
    assertThat(batchOperation.operationsCompletedCount()).isEqualTo(0);
    assertThat(batchOperation.operationsFailedCount()).isEqualTo(1);
    assertThat(batchOperation.state()).isEqualTo(BatchOperationState.ACTIVE);
  }

  @Test
  public void shouldMonitorModifyProcessInstanceBatchOperation() {
    // given
    final var batchOperationCreatedRecord =
        getBatchOperationCreatedRecord(1L, BatchOperationType.MODIFY_PROCESS_INSTANCE);
    final var batchOperationKey = batchOperationCreatedRecord.getKey();
    final var batchOperationChunkRecord = getBatchOperationChunkRecord(batchOperationKey, 2L);
    final var processInstanceKey = 1L;
    final var processInstanceModifiedRecord =
        getBatchOperationModifyProcessInstanceRecord(processInstanceKey, batchOperationKey, 3L);

    // when
    exporter.export(batchOperationCreatedRecord);
    exporter.export(batchOperationChunkRecord);
    exporter.export(processInstanceModifiedRecord);

    // then
    final var batchOperation =
        rdbmsService.getBatchOperationReader().findOne(String.valueOf(batchOperationKey)).get();
    assertThat(batchOperation).isNotNull();
    assertThat(batchOperation.operationsTotalCount()).isEqualTo(3);
    assertThat(batchOperation.operationsCompletedCount()).isEqualTo(1);
    assertThat(batchOperation.operationsFailedCount()).isEqualTo(0);
    assertThat(batchOperation.state()).isEqualTo(BatchOperationState.ACTIVE);
  }

  @Test
  public void shouldMonitorFailedModifyProcessInstanceBatchOperation() {
    // given
    final var batchOperationCreatedRecord =
        getBatchOperationCreatedRecord(1L, BatchOperationType.MODIFY_PROCESS_INSTANCE);
    final var batchOperationKey = batchOperationCreatedRecord.getKey();
    final var batchOperationChunkRecord = getBatchOperationChunkRecord(batchOperationKey, 2L);
    final var processInstanceKey = 1L;
    final var modifyProcessIncidentFailedRecord =
        getFailedBatchOperationModifyProcessInstanceRecord(
            processInstanceKey, batchOperationKey, 3L);

    // when
    exporter.export(batchOperationCreatedRecord);
    exporter.export(batchOperationChunkRecord);
    exporter.export(modifyProcessIncidentFailedRecord);

    // then
    final var batchOperation =
        rdbmsService.getBatchOperationReader().findOne(String.valueOf(batchOperationKey)).get();
    assertThat(batchOperation).isNotNull();
    assertThat(batchOperation.operationsTotalCount()).isEqualTo(3);
    assertThat(batchOperation.operationsCompletedCount()).isEqualTo(0);
    assertThat(batchOperation.operationsFailedCount()).isEqualTo(1);
    assertThat(batchOperation.state()).isEqualTo(BatchOperationState.ACTIVE);
  }

  private List<BatchOperationItemEntity> getItems(final long batchOperationKey) {
    return rdbmsService
        .getBatchOperationItemReader()
        .search(
            SearchQueryBuilders.batchOperationItemQuery(
                q -> q.filter(f -> f.batchOperationKeys(Long.toString(batchOperationKey)))))
        .items();
  }
}
