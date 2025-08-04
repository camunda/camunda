/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.batchoperation;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import io.camunda.db.rdbms.write.RdbmsWriterConfig;
import io.camunda.it.rdbms.db.fixtures.BatchOperationFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.BatchOperationType;
import io.camunda.search.query.BatchOperationItemQuery;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class BatchOperationHistoryCleanupIT {

  @TestTemplate
  public void shouldCleanupBatchOperations(final CamundaRdbmsTestApplication testApplication) {
    // GIVEN
    final var rdbmsService = testApplication.getRdbmsService();
    final var rdbmsWriter = rdbmsService.createWriter(0);
    final var historyCleanupService = rdbmsWriter.getHistoryCleanupService();
    final var batchOperationReader = rdbmsService.getBatchOperationReader();
    final var batchOperationItemReader = rdbmsService.getBatchOperationItemReader();

    final var batchOperation =
        BatchOperationFixtures.createAndSaveBatchOperation(rdbmsWriter, b -> b);
    final var batchOperationKey = batchOperation.batchOperationKey();
    final BatchOperationType batchOperationType = batchOperation.operationType();

    BatchOperationFixtures.createAndSaveRandomBatchOperationItems(
        rdbmsWriter, batchOperationKey, 5);

    // AND we schedule history cleanup
    final OffsetDateTime now = OffsetDateTime.now();
    historyCleanupService.scheduleBatchOperationForHistoryCleanup(
        batchOperationKey, batchOperationType, now);
    rdbmsWriter.flush();

    // WHEN we do the history cleanup (partition doesn't matter here)
    final OffsetDateTime cleanupDate =
        now.plus(RdbmsWriterConfig.HistoryConfig.DEFAULT_BATCH_OPERATION_HISTORY_TTL)
            .plusSeconds(1);
    historyCleanupService.cleanupHistory(0, cleanupDate);

    // THEN
    assertThat(batchOperationReader.exists(batchOperationKey)).isFalse();
    final var query =
        BatchOperationItemQuery.of(b -> b.filter(f -> f.batchOperationKeys(batchOperationKey)));
    assertThat(batchOperationItemReader.search(query).total()).isEqualTo(0);
  }

  @TestTemplate
  public void shouldNOTCleanupBatchOperations(final CamundaRdbmsTestApplication testApplication) {
    // GIVEN
    final var rdbmsService = testApplication.getRdbmsService();
    final var rdbmsWriter = rdbmsService.createWriter(0);
    final var historyCleanupService = rdbmsWriter.getHistoryCleanupService();
    final var batchOperationReader = rdbmsService.getBatchOperationReader();
    final var batchOperationItemReader = rdbmsService.getBatchOperationItemReader();

    final var batchOperation =
        BatchOperationFixtures.createAndSaveBatchOperation(rdbmsWriter, b -> b);
    final var batchOperationKey = batchOperation.batchOperationKey();
    final BatchOperationType batchOperationType = batchOperation.operationType();

    BatchOperationFixtures.createAndSaveRandomBatchOperationItems(
        rdbmsWriter, batchOperationKey, 5);

    // AND we schedule history cleanup
    final OffsetDateTime now = OffsetDateTime.now();
    historyCleanupService.scheduleBatchOperationForHistoryCleanup(
        batchOperationKey, batchOperationType, now);
    rdbmsWriter.flush();

    // WHEN we do the history cleanup too early (partition doesn't matter here)
    historyCleanupService.cleanupHistory(0, now);

    // THEN
    assertThat(batchOperationReader.exists(batchOperationKey)).isTrue();
    final var query =
        BatchOperationItemQuery.of(b -> b.filter(f -> f.batchOperationKeys(batchOperationKey)));
    assertThat(batchOperationItemReader.search(query).total()).isEqualTo(5);
  }
}
