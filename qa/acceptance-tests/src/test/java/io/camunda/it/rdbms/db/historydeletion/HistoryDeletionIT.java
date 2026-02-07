/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.historydeletion;

import static io.camunda.db.rdbms.write.domain.HistoryDeletionDbModel.HistoryDeletionTypeDbModel.PROCESS_INSTANCE;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.HistoryDeletionDbReader;
import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.db.rdbms.write.domain.HistoryDeletionDbModel;
import io.camunda.db.rdbms.write.service.HistoryDeletionWriter;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class HistoryDeletionIT {

  private static final int PARTITION_ID = 0;
  private static final Long RESOURCE_KEY = 2251799813685312L;
  private static final Long BATCH_OPERATION_KEY = 2251799813685385L;

  private CamundaRdbmsTestApplication testApplication;
  private RdbmsWriters rdbmsWriters;
  private HistoryDeletionDbReader historyDeletionReader;
  private HistoryDeletionWriter historyDeletionWriter;

  @BeforeEach
  void setUp() {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    historyDeletionReader = rdbmsService.getHistoryDeletionDbReader();
    historyDeletionWriter = rdbmsWriters.getHistoryDeletionWriter();
  }

  @AfterEach
  void tearDown() {
    rdbmsWriters.getRdbmsPurger().purgeRdbms();
  }

  @TestTemplate
  public void shouldInsertHistoryDeletion() {
    // given
    final var model =
        new HistoryDeletionDbModel(
            RESOURCE_KEY, PROCESS_INSTANCE, BATCH_OPERATION_KEY, PARTITION_ID);
    historyDeletionWriter.create(model);
    rdbmsWriters.flush();

    // when
    final var actual = historyDeletionReader.getNextBatch(PARTITION_ID, 1);

    // then
    assertThat(actual.historyDeletionModels()).containsExactly(model);
  }

  @TestTemplate
  public void shouldBeEmpty() {
    // when
    final var actual = historyDeletionReader.getNextBatch(PARTITION_ID, 1);

    // then
    assertThat(actual.historyDeletionModels()).isEmpty();
  }

  @TestTemplate
  public void shouldInsertAndRetrieveMultipleModels() {
    // given
    final var model1 =
        new HistoryDeletionDbModel.Builder()
            .resourceKey(1L)
            .resourceType(PROCESS_INSTANCE)
            .batchOperationKey(10L)
            .partitionId(PARTITION_ID)
            .build();
    final var model2 =
        new HistoryDeletionDbModel.Builder()
            .resourceKey(2L)
            .resourceType(PROCESS_INSTANCE)
            .batchOperationKey(11L)
            .partitionId(PARTITION_ID)
            .build();
    historyDeletionWriter.create(model1);
    historyDeletionWriter.create(model2);
    rdbmsWriters.flush();

    // when
    final var actual = historyDeletionReader.getNextBatch(PARTITION_ID, 10);

    // then
    assertThat(actual.historyDeletionModels()).containsExactlyInAnyOrder(model1, model2);
  }

  @TestTemplate
  public void shouldFilterByPartition() {
    // given
    final var modelPartition0 =
        new HistoryDeletionDbModel.Builder()
            .resourceKey(1L)
            .resourceType(PROCESS_INSTANCE)
            .batchOperationKey(10L)
            .partitionId(0)
            .build();
    final var modelPartition1 =
        new HistoryDeletionDbModel.Builder()
            .resourceKey(2L)
            .resourceType(PROCESS_INSTANCE)
            .batchOperationKey(11L)
            .partitionId(1)
            .build();
    historyDeletionWriter.create(modelPartition0);
    historyDeletionWriter.create(modelPartition1);
    rdbmsWriters.flush();

    // when
    final var actualPartition0 = historyDeletionReader.getNextBatch(0, 10);
    final var actualPartition1 = historyDeletionReader.getNextBatch(1, 10);

    // then
    assertThat(actualPartition0.historyDeletionModels()).containsExactly(modelPartition0);
    assertThat(actualPartition1.historyDeletionModels()).containsExactly(modelPartition1);
  }

  @TestTemplate
  public void shouldSortByBatchOperationKeyAndResourceKey() {
    // given
    final var modelA =
        new HistoryDeletionDbModel.Builder()
            .resourceKey(2L)
            .resourceType(PROCESS_INSTANCE)
            .batchOperationKey(10L)
            .partitionId(PARTITION_ID)
            .build();
    final var modelB =
        new HistoryDeletionDbModel.Builder()
            .resourceKey(1L)
            .resourceType(PROCESS_INSTANCE)
            .batchOperationKey(10L)
            .partitionId(PARTITION_ID)
            .build();
    final var modelC =
        new HistoryDeletionDbModel.Builder()
            .resourceKey(3L)
            .resourceType(PROCESS_INSTANCE)
            .batchOperationKey(9L)
            .partitionId(PARTITION_ID)
            .build();
    historyDeletionWriter.create(modelA);
    historyDeletionWriter.create(modelB);
    historyDeletionWriter.create(modelC);
    rdbmsWriters.flush();

    // when
    final var actual = historyDeletionReader.getNextBatch(PARTITION_ID, 10);

    // then
    assertThat(actual.historyDeletionModels())
        .containsExactly(
            modelC, // batchOperationKey=9, resourceKey=3
            modelB, // batchOperationKey=10, resourceKey=1
            modelA // batchOperationKey=10, resourceKey=2
            );
  }

  @TestTemplate
  public void shouldDeleteHistoryDeletion() {
    // given
    final var model =
        new HistoryDeletionDbModel(
            RESOURCE_KEY, PROCESS_INSTANCE, BATCH_OPERATION_KEY, PARTITION_ID);
    historyDeletionWriter.create(model);
    rdbmsWriters.flush();
    assertThat(historyDeletionReader.getNextBatch(PARTITION_ID, 1).historyDeletionModels())
        .isNotEmpty();

    // when
    historyDeletionWriter.delete(RESOURCE_KEY, BATCH_OPERATION_KEY);
    rdbmsWriters.flush();

    // then
    assertThat(historyDeletionReader.getNextBatch(PARTITION_ID, 1).historyDeletionModels())
        .isEmpty();
  }
}
