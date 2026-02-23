/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.exporter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import io.camunda.db.rdbms.LiquibaseSchemaManager;
import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.exporter.rdbms.RdbmsExporterWrapper;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationItemEntity;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationItemState;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationState;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.zeebe.broker.exporter.context.ExporterConfiguration;
import io.camunda.zeebe.broker.exporter.context.ExporterContext;
import io.camunda.zeebe.exporter.test.ExporterTestController;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.Properties;
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
      "camunda.data.secondary-storage.type=rdbms",
      "camunda.data.secondary-storage.rdbms.queue-size=0",
    })
class RdbmsExporterBatchOperationsIT {

  private static final RecordFixtures FIXTURES = new RecordFixtures();
  private final ExporterTestController controller = new ExporterTestController();
  private final VendorDatabaseProperties vendorDatabaseProperties =
      new VendorDatabaseProperties(
          new Properties() {
            {
              setProperty("variableValue.previewSize", "100");
              setProperty("userCharColumn.size", "50");
              setProperty("errorMessage.size", "500");
              setProperty("treePath.size", "500");
              setProperty("disableFkBeforeTruncate", "true");
            }
          });
  @Autowired private LiquibaseSchemaManager liquibaseSchemaManager;
  @Autowired private RdbmsService rdbmsService;
  private RdbmsExporterWrapper exporter;

  @BeforeEach
  void setUp() {
    setup(true);
  }

  private void setup(final boolean exportPendingItems) {
    exporter =
        new RdbmsExporterWrapper(rdbmsService, liquibaseSchemaManager, vendorDatabaseProperties);
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
  public void shouldExportCreatedBatchOperation() {
    // given
    final var batchOperationCreatedRecord = FIXTURES.getBatchOperationCreatedRecord();
    final var batchOperationKey = batchOperationCreatedRecord.getKey();

    // when
    exporter.export(batchOperationCreatedRecord);

    // then
    final var batchOperation =
        rdbmsService.getBatchOperationReader().findOne(String.valueOf(batchOperationKey));
    assertThat(batchOperation)
        .hasValueSatisfying(
            entity -> {
              assertThat(entity.state()).isEqualTo(BatchOperationState.CREATED);
              assertThat(entity.startDate()).isNull();
            });
  }

  @Test
  public void shouldExportInitializedBatchOperation() {
    // given
    final var batchOperationCreatedRecord = FIXTURES.getBatchOperationCreatedRecord();
    final var batchOperationKey = batchOperationCreatedRecord.getKey();
    final var batchOperationInitializedRecord =
        FIXTURES.getBatchOperationInitializedRecord(batchOperationKey);
    final var batchOperationChunkRecord = FIXTURES.getBatchOperationChunkRecord(batchOperationKey);

    // when
    exporter.export(batchOperationCreatedRecord);
    exporter.export(batchOperationInitializedRecord);
    exporter.export(batchOperationChunkRecord);

    // then
    final var batchOperation =
        rdbmsService.getBatchOperationReader().findOne(String.valueOf(batchOperationKey));
    assertThat(batchOperation)
        .hasValueSatisfying(
            entity -> {
              assertThat(entity.operationsTotalCount()).isEqualTo(3);
              assertThat(entity.state()).isEqualTo(BatchOperationState.ACTIVE);
              assertThat(entity.startDate()).isNotNull();
            });
  }

  @Test
  public void shouldExportCompletedBatchOperation() {
    // given
    final var batchOperationKey = givenBatchOperationIsCreatedAndIsActive();
    final var batchOperationCompletedRecord =
        FIXTURES.getBatchOperationCompletedRecord(batchOperationKey);

    // when
    exporter.export(batchOperationCompletedRecord);

    // then
    final var batchOperation =
        rdbmsService.getBatchOperationReader().findOne(String.valueOf(batchOperationKey)).get();
    assertThat(batchOperation.state()).isEqualTo(BatchOperationState.COMPLETED);
  }

  @Test
  public void shouldExportCompletedBatchOperationWithErrors() {
    // given
    final var batchOperationKey = givenBatchOperationIsCreatedAndIsActive();
    final var completedWithErrorsRecord =
        FIXTURES.getBatchOperationCompletedWithErrorsRecord(batchOperationKey);

    // when
    exporter.export(completedWithErrorsRecord);

    // then
    final var batchOperation =
        rdbmsService.getBatchOperationReader().findOne(String.valueOf(batchOperationKey)).get();
    assertThat(batchOperation.state()).isEqualTo(BatchOperationState.PARTIALLY_COMPLETED);
    assertThat(batchOperation.errors()).isNotEmpty();
  }

  @Test
  public void shouldExportFailedBatchOperation() {
    // given
    final var batchOperationKey = givenBatchOperationIsCreatedAndIsActive();
    final var failedRecord =
        FIXTURES.generateLifecycleRecord(batchOperationKey, BatchOperationIntent.FAILED, true);

    // when
    exporter.export(failedRecord);

    // then
    final var batchOperation =
        rdbmsService.getBatchOperationReader().findOne(String.valueOf(batchOperationKey)).get();
    assertThat(batchOperation.state()).isEqualTo(BatchOperationState.FAILED);
    assertThat(batchOperation.errors()).isNotEmpty();
  }

  @Test
  public void shouldCancelBatchOperation() {
    // given
    final var batchOperationKey = givenBatchOperationIsCreatedAndIsActive();
    final var batchOperationCanceledRecord =
        FIXTURES.getBatchOperationLifecycleCanceledRecord(batchOperationKey);

    // when
    exporter.export(batchOperationCanceledRecord);

    // then
    final var batchOperation =
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
    final var batchOperationKey = givenBatchOperationIsCreatedAndIsActive();

    final var batchOperationSuspendedRecord =
        FIXTURES.getBatchOperationLifecycleSuspendedRecord(batchOperationKey);
    // when
    exporter.export(batchOperationSuspendedRecord);

    // then
    final var batchOperation =
        rdbmsService.getBatchOperationReader().findOne(String.valueOf(batchOperationKey)).get();
    assertThat(batchOperation.state()).isEqualTo(BatchOperationState.SUSPENDED);
  }

  @Test
  public void shouldResumeBatchOperation() {
    // given
    final var batchOperationKey = givenBatchOperationIsCreatedAndIsActive();
    final var batchOperationSuspendedRecord =
        FIXTURES.getBatchOperationLifecycleSuspendedRecord(batchOperationKey);
    exporter.export(batchOperationSuspendedRecord);

    final var batchOperationResumeRecord =
        FIXTURES.getBatchOperationLifecycleResumeRecord(batchOperationKey);
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
    final var batchOperationCreatedRecord = FIXTURES.getBatchOperationCreatedRecord();
    final var batchOperationKey = batchOperationCreatedRecord.getKey();
    final var batchOperationInitializedRecord =
        FIXTURES.getBatchOperationInitializedRecord(batchOperationKey);
    final var batchOperationChunkRecord = FIXTURES.getBatchOperationChunkRecord(batchOperationKey);
    final var processInstanceKey = 1L;
    final var canceledProcessRecord =
        FIXTURES.getCanceledProcessRecord(processInstanceKey, batchOperationKey);

    // when
    exporter.export(batchOperationCreatedRecord);
    exporter.export(batchOperationInitializedRecord);
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
    final var batchOperationCreatedRecord = FIXTURES.getBatchOperationCreatedRecord();
    final var batchOperationKey = batchOperationCreatedRecord.getKey();
    final var batchOperationInitializedRecord =
        FIXTURES.getBatchOperationInitializedRecord(batchOperationKey);
    final var batchOperationChunkRecord = FIXTURES.getBatchOperationChunkRecord(batchOperationKey);
    final var processInstanceKey = 1L;
    final var canceledProcessRecord =
        FIXTURES.getCanceledProcessRecord(processInstanceKey, batchOperationKey);

    // when
    exporter.export(batchOperationCreatedRecord);
    exporter.export(batchOperationInitializedRecord);
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
    final var batchOperationCreatedRecord = FIXTURES.getBatchOperationCreatedRecord();
    final var batchOperationKey = batchOperationCreatedRecord.getKey();
    final var batchOperationInitializedRecord =
        FIXTURES.getBatchOperationInitializedRecord(batchOperationKey);
    final var batchOperationChunkRecord = FIXTURES.getBatchOperationChunkRecord(batchOperationKey);
    final var processInstanceKey = 1L;
    final var rejectedCancelProcessRecord =
        FIXTURES.getRejectedCancelProcessRecord(processInstanceKey, batchOperationKey);

    // when
    exporter.export(batchOperationCreatedRecord);
    exporter.export(batchOperationInitializedRecord);
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
    final var batchOperationCreatedRecord = FIXTURES.getBatchOperationCreatedRecord();
    final var batchOperationKey = batchOperationCreatedRecord.getKey();
    final var batchOperationInitializedRecord =
        FIXTURES.getBatchOperationInitializedRecord(batchOperationKey);
    final var batchOperationChunkRecord = FIXTURES.getBatchOperationChunkRecord(batchOperationKey);
    final var processInstanceKey = 1L;
    final var rejectedCancelProcessRecord =
        FIXTURES.getRejectedCancelProcessRecord(processInstanceKey, batchOperationKey);

    // when
    exporter.export(batchOperationCreatedRecord);
    exporter.export(batchOperationInitializedRecord);
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
        FIXTURES.getBatchOperationCreatedRecord(BatchOperationType.RESOLVE_INCIDENT);
    final var batchOperationKey = batchOperationCreatedRecord.getKey();
    final var batchOperationInitializedRecord =
        FIXTURES.getBatchOperationInitializedRecord(batchOperationKey);
    final var batchOperationChunkRecord = FIXTURES.getBatchOperationChunkRecord(batchOperationKey);
    final var incidentKey = 1L;
    final var incidentResolvedRecord =
        FIXTURES.getBatchOperationResolveIncidentRecord(incidentKey, batchOperationKey);

    // when
    exporter.export(batchOperationCreatedRecord);
    exporter.export(batchOperationInitializedRecord);
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
        FIXTURES.getBatchOperationCreatedRecord(BatchOperationType.RESOLVE_INCIDENT);
    final var batchOperationKey = batchOperationCreatedRecord.getKey();
    final var batchOperationInitializedRecord =
        FIXTURES.getBatchOperationInitializedRecord(batchOperationKey);
    final var batchOperationChunkRecord = FIXTURES.getBatchOperationChunkRecord(batchOperationKey);
    final var incidentKey = 1L;
    final var resolveIncidentFailedRecord =
        FIXTURES.getFailedBatchOperationResolveIncidentRecord(incidentKey, batchOperationKey);

    // when
    exporter.export(batchOperationCreatedRecord);
    exporter.export(batchOperationInitializedRecord);
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
        FIXTURES.getBatchOperationCreatedRecord(BatchOperationType.MIGRATE_PROCESS_INSTANCE);
    final var batchOperationKey = batchOperationCreatedRecord.getKey();
    final var batchOperationInitializedRecord =
        FIXTURES.getBatchOperationInitializedRecord(batchOperationKey);
    final var batchOperationChunkRecord = FIXTURES.getBatchOperationChunkRecord(batchOperationKey);
    final var processInstanceKey = 1L;
    final var processMigratedRecord =
        FIXTURES.getBatchOperationProcessMigratedRecord(processInstanceKey, batchOperationKey);

    // when
    exporter.export(batchOperationCreatedRecord);
    exporter.export(batchOperationInitializedRecord);
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
        FIXTURES.getBatchOperationCreatedRecord(BatchOperationType.MIGRATE_PROCESS_INSTANCE);
    final var batchOperationKey = batchOperationCreatedRecord.getKey();
    final var batchOperationInitializedRecord =
        FIXTURES.getBatchOperationInitializedRecord(batchOperationKey);
    final var batchOperationChunkRecord = FIXTURES.getBatchOperationChunkRecord(batchOperationKey);
    final var processInstanceKey = 1L;
    final var processFailedMigrateRecord =
        FIXTURES.getFailedBatchOperationProcessMigratedRecord(
            processInstanceKey, batchOperationKey);

    // when
    exporter.export(batchOperationCreatedRecord);
    exporter.export(batchOperationInitializedRecord);
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
        FIXTURES.getBatchOperationCreatedRecord(BatchOperationType.MODIFY_PROCESS_INSTANCE);
    final var batchOperationKey = batchOperationCreatedRecord.getKey();
    final var batchOperationInitializedRecord =
        FIXTURES.getBatchOperationInitializedRecord(batchOperationKey);
    final var batchOperationChunkRecord = FIXTURES.getBatchOperationChunkRecord(batchOperationKey);
    final var processInstanceKey = 1L;
    final var processInstanceModifiedRecord =
        FIXTURES.getBatchOperationModifyProcessInstanceRecord(
            processInstanceKey, batchOperationKey);

    // when
    exporter.export(batchOperationCreatedRecord);
    exporter.export(batchOperationInitializedRecord);
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
        FIXTURES.getBatchOperationCreatedRecord(BatchOperationType.MODIFY_PROCESS_INSTANCE);
    final var batchOperationKey = batchOperationCreatedRecord.getKey();
    final var batchOperationInitializedRecord =
        FIXTURES.getBatchOperationInitializedRecord(batchOperationKey);
    final var batchOperationChunkRecord = FIXTURES.getBatchOperationChunkRecord(batchOperationKey);
    final var processInstanceKey = 1L;
    final var modifyProcessIncidentFailedRecord =
        FIXTURES.getFailedBatchOperationModifyProcessInstanceRecord(
            processInstanceKey, batchOperationKey);

    // when
    exporter.export(batchOperationCreatedRecord);
    exporter.export(batchOperationInitializedRecord);
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

  private long givenBatchOperationIsCreatedAndIsActive() {
    final var batchOperationCreatedRecord = FIXTURES.getBatchOperationCreatedRecord();
    final var batchOperationKey = batchOperationCreatedRecord.getKey();
    final var batchOperationInitializedRecord =
        FIXTURES.getBatchOperationInitializedRecord(batchOperationKey);
    final var batchOperationChunkRecord = FIXTURES.getBatchOperationChunkRecord(batchOperationKey);

    exporter.export(batchOperationCreatedRecord);
    exporter.export(batchOperationInitializedRecord);
    exporter.export(batchOperationChunkRecord);
    final var batchOperation =
        rdbmsService.getBatchOperationReader().findOne(String.valueOf(batchOperationKey)).get();

    assumeThat(batchOperation).isNotNull();
    assumeThat(batchOperation.operationsTotalCount()).isEqualTo(3);
    assumeThat(batchOperation.state()).isEqualTo(BatchOperationState.ACTIVE);
    return batchOperationKey;
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
