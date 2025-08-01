/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.batchoperation;

import static io.camunda.it.rdbms.db.fixtures.BatchOperationFixtures.createAndSaveBatchOperation;
import static io.camunda.it.rdbms.db.fixtures.BatchOperationFixtures.createAndSaveRandomBatchOperationItems;
import static io.camunda.it.rdbms.db.fixtures.BatchOperationFixtures.createAndSaveRandomBatchOperations;
import static io.camunda.it.rdbms.db.fixtures.BatchOperationFixtures.createSaveReturnRandomBatchOperationItems;
import static io.camunda.it.rdbms.db.fixtures.BatchOperationFixtures.insertBatchOperationsItems;
import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.NOW;
import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.nextKey;
import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.nextStringKey;
import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.randomEnum;
import static io.camunda.util.FilterUtil.mapDefaultToOperation;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.sql.BatchOperationMapper.BatchOperationErrorDto;
import io.camunda.db.rdbms.sql.BatchOperationMapper.BatchOperationErrorsDto;
import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.db.rdbms.write.domain.BatchOperationDbModel;
import io.camunda.db.rdbms.write.domain.BatchOperationItemDbModel;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.BatchOperationEntity;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationItemEntity;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationItemState;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationState;
import io.camunda.search.entities.BatchOperationType;
import io.camunda.search.filter.BatchOperationFilter;
import io.camunda.search.filter.BatchOperationItemFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.BatchOperationItemQuery;
import io.camunda.search.query.BatchOperationQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.sort.BatchOperationItemSort;
import io.camunda.search.sort.BatchOperationSort;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.assertj.core.data.TemporalUnitWithinOffset;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class BatchOperationIT {
  @TestTemplate
  public void shouldReturnTrueForExists(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final var batchOperation =
        createAndSaveRandomBatchOperations(rdbmsService.createWriter(0), b -> b).getLast();

    final var searchResult =
        rdbmsService.getBatchOperationReader().exists(batchOperation.batchOperationKey());

    assertThat(searchResult).isTrue();
  }

  @TestTemplate
  public void shouldReturnFalseForExists(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    createAndSaveRandomBatchOperations(rdbmsService.createWriter(0), b -> b);

    final var searchResult = rdbmsService.getBatchOperationReader().exists(nextStringKey());

    assertThat(searchResult).isFalse();
  }

  @TestTemplate
  public void shouldInsertBatchItems(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    // given
    final RdbmsWriter writer = rdbmsService.createWriter(0);
    final var batchOperation =
        createAndSaveBatchOperation(
            writer, b -> b.state(BatchOperationState.ACTIVE).endDate(null).operationsTotalCount(0));

    // when
    insertBatchOperationsItems(writer, batchOperation.batchOperationKey(), Set.of(nextKey()));
    insertBatchOperationsItems(
        writer, batchOperation.batchOperationKey(), Set.of(nextKey(), nextKey()));

    // then
    final var updatedBatchOperation = getBatchOperation(rdbmsService, batchOperation);

    // batch is updated
    assertThat(updatedBatchOperation).isNotNull();
    final BatchOperationEntity batchOperationEntity = updatedBatchOperation.items().getFirst();
    assertThat(batchOperationEntity.endDate()).isNull();
    assertThat(batchOperationEntity.operationsTotalCount()).isEqualTo(3);
    assertThat(batchOperationEntity.state()).isEqualTo(BatchOperationState.ACTIVE);

    // and items are there
    final var updatedItems =
        getBatchOperationItems(rdbmsService, batchOperation.batchOperationKey());
    assertThat(updatedItems).isNotNull();
    assertThat(updatedItems.items()).hasSize(3);
    assertThat(updatedItems.items().stream().map(BatchOperationItemEntity::state))
        .containsOnly(BatchOperationItemState.ACTIVE);
  }

  @TestTemplate
  public void shouldUpdateCompletedBatchItem(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    // given
    final RdbmsWriter writer = rdbmsService.createWriter(0);
    final var batchOperation =
        createAndSaveBatchOperation(
            writer,
            b ->
                b.state(BatchOperationState.ACTIVE)
                    .endDate(null)
                    .operationsTotalCount(0)
                    .operationsCompletedCount(0));

    final var items =
        createAndSaveRandomBatchOperationItems(writer, batchOperation.batchOperationKey(), 2);

    // when
    writer
        .getBatchOperationWriter()
        .updateItem(
            new BatchOperationItemDbModel(
                batchOperation.batchOperationKey(),
                items.getFirst().itemKey(),
                items.getFirst().processInstanceKey(),
                BatchOperationItemState.COMPLETED,
                NOW,
                null));
    writer.flush();

    // then
    final var updatedBatchOperation = getBatchOperation(rdbmsService, batchOperation);

    assertThat(updatedBatchOperation).isNotNull();
    final BatchOperationEntity batchOperationEntity = updatedBatchOperation.items().getFirst();
    assertThat(batchOperationEntity.endDate()).isNull();
    assertThat(batchOperationEntity.operationsTotalCount()).isEqualTo(2);
    assertThat(batchOperationEntity.operationsCompletedCount()).isEqualTo(1);
    assertThat(batchOperationEntity.state()).isEqualTo(BatchOperationState.ACTIVE);

    // and items have correct state
    final var updatedItems =
        getBatchOperationItems(rdbmsService, batchOperation.batchOperationKey()).items();
    assertThat(updatedItems).isNotNull();
    assertThat(updatedItems).hasSize(2);
    final var firstItem =
        updatedItems.stream()
            .filter(i -> Objects.equals(i.itemKey(), items.getFirst().itemKey()))
            .findFirst()
            .get();
    assertThat(firstItem.state()).isEqualTo(BatchOperationItemState.COMPLETED);
    assertThat(firstItem.processedDate())
        .isCloseTo(NOW, new TemporalUnitWithinOffset(1, ChronoUnit.MILLIS));
    assertThat(firstItem.errorMessage()).isNull();

    final var lastItem =
        updatedItems.stream()
            .filter(i -> Objects.equals(i.itemKey(), items.getLast().itemKey()))
            .findFirst()
            .get();
    assertThat(lastItem.state()).isEqualTo(BatchOperationItemState.ACTIVE);
  }

  @TestTemplate
  public void shouldUpdateCompletedBatchItemWithLargeErrorMessage(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    // given
    final RdbmsWriter writer = rdbmsService.createWriter(0);
    final var batchOperation =
        createAndSaveBatchOperation(
            writer,
            b ->
                b.state(BatchOperationState.ACTIVE)
                    .endDate(null)
                    .operationsTotalCount(0)
                    .operationsCompletedCount(0));

    final var item =
        createAndSaveRandomBatchOperationItems(writer, batchOperation.batchOperationKey(), 1)
            .getFirst();

    // when
    writer
        .getBatchOperationWriter()
        .updateItem(
            new BatchOperationItemDbModel(
                batchOperation.batchOperationKey(),
                item.itemKey(),
                item.processInstanceKey(),
                BatchOperationItemState.COMPLETED,
                NOW,
                "x".repeat(9000)));
    writer.flush();

    // then
    final var updatedBatchOperation = getBatchOperation(rdbmsService, batchOperation);

    assertThat(updatedBatchOperation).isNotNull();
    final BatchOperationEntity batchOperationEntity = updatedBatchOperation.items().getFirst();
    assertThat(batchOperationEntity.endDate()).isNull();
    assertThat(batchOperationEntity.operationsTotalCount()).isEqualTo(1);
    assertThat(batchOperationEntity.operationsCompletedCount()).isEqualTo(1);
    assertThat(batchOperationEntity.state()).isEqualTo(BatchOperationState.ACTIVE);

    final var updatedItems =
        getBatchOperationItems(rdbmsService, batchOperation.batchOperationKey()).items();
    assertThat(updatedItems).isNotNull();
    assertThat(updatedItems).hasSize(1);

    final var updatedItem = updatedItems.getFirst();

    assertThat(updatedItem.state()).isEqualTo(BatchOperationItemState.COMPLETED);
    assertThat(updatedItem.processedDate())
        .isCloseTo(NOW, new TemporalUnitWithinOffset(1, ChronoUnit.MILLIS));
    assertThat(updatedItem.errorMessage()).isNotNull();
    assertThat(updatedItem.errorMessage().length()).isEqualTo(4000);
  }

  @TestTemplate
  public void shouldUpdateCompletedBatchItemWithoutInitialExport(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    // given
    final RdbmsWriter writer =
        rdbmsService.createWriter(b -> b.partitionId(0).exportBatchOperationItemsOnCreation(false));
    final var batchOperation =
        createAndSaveBatchOperation(writer, b -> b.state(BatchOperationState.ACTIVE).endDate(null));

    final var items =
        createAndSaveRandomBatchOperationItems(writer, batchOperation.batchOperationKey(), 2);

    // when
    writer
        .getBatchOperationWriter()
        .updateItem(
            new BatchOperationItemDbModel(
                batchOperation.batchOperationKey(),
                items.getFirst().itemKey(),
                items.getFirst().processInstanceKey(),
                BatchOperationItemState.COMPLETED,
                NOW,
                null));
    writer.flush();

    // then
    final var updatedBatchOperation = getBatchOperation(rdbmsService, batchOperation);

    assertThat(updatedBatchOperation).isNotNull();
    final BatchOperationEntity batchOperationEntity = updatedBatchOperation.items().getFirst();
    assertThat(batchOperationEntity.endDate()).isNull();
    assertThat(batchOperationEntity.operationsTotalCount()).isEqualTo(2);
    assertThat(batchOperationEntity.operationsCompletedCount()).isEqualTo(1);
    assertThat(batchOperationEntity.state()).isEqualTo(BatchOperationState.ACTIVE);

    // and only one item exists
    final var updatedItems =
        getBatchOperationItems(rdbmsService, batchOperation.batchOperationKey()).items();
    assertThat(updatedItems).isNotNull();
    assertThat(updatedItems).hasSize(1);
    final var firstItem = updatedItems.getFirst();
    assertThat(firstItem.state()).isEqualTo(BatchOperationItemState.COMPLETED);
    assertThat(firstItem.processedDate())
        .isCloseTo(NOW, new TemporalUnitWithinOffset(1, ChronoUnit.MILLIS));
    assertThat(firstItem.errorMessage()).isNull();
  }

  @TestTemplate
  public void shouldUpdateFailedBatchItem(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    // given
    final RdbmsWriter writer = rdbmsService.createWriter(0);
    final var batchOperation =
        createAndSaveBatchOperation(
            writer,
            b ->
                b.state(BatchOperationState.ACTIVE)
                    .endDate(null)
                    .operationsTotalCount(0)
                    .operationsFailedCount(0));

    final var items =
        createAndSaveRandomBatchOperationItems(writer, batchOperation.batchOperationKey(), 2);

    // when
    writer
        .getBatchOperationWriter()
        .updateItem(
            new BatchOperationItemDbModel(
                batchOperation.batchOperationKey(),
                items.getFirst().itemKey(),
                items.getFirst().processInstanceKey(),
                BatchOperationItemState.FAILED,
                NOW,
                "error"));
    writer.flush();

    // then
    final var updatedBatchOperation = getBatchOperation(rdbmsService, batchOperation);

    assertThat(updatedBatchOperation).isNotNull();
    final BatchOperationEntity batchOperationEntity = updatedBatchOperation.items().getFirst();
    assertThat(batchOperationEntity.endDate()).isNull();
    assertThat(batchOperationEntity.operationsTotalCount()).isEqualTo(2);
    assertThat(batchOperationEntity.operationsFailedCount()).isEqualTo(1);
    assertThat(batchOperationEntity.state()).isEqualTo(BatchOperationState.ACTIVE);

    // and items have correct state
    final var updatedItems =
        getBatchOperationItems(rdbmsService, batchOperation.batchOperationKey()).items();
    assertThat(updatedItems).isNotNull();
    assertThat(updatedItems).hasSize(2);
    final var firstItem =
        updatedItems.stream()
            .filter(i -> Objects.equals(i.itemKey(), items.getFirst().itemKey()))
            .findFirst()
            .orElseThrow();
    assertThat(firstItem.state()).isEqualTo(BatchOperationItemState.FAILED);
    assertThat(firstItem.processedDate())
        .isCloseTo(NOW, new TemporalUnitWithinOffset(1, ChronoUnit.MILLIS));
    assertThat(firstItem.errorMessage()).isEqualTo("error");

    final var lastItem =
        updatedItems.stream()
            .filter(i -> Objects.equals(i.itemKey(), items.getLast().itemKey()))
            .findFirst()
            .orElseThrow();
    assertThat(lastItem.state()).isEqualTo(BatchOperationItemState.ACTIVE);
  }

  @TestTemplate
  public void shouldUpdateSkippedBatchItem(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    // given
    final RdbmsWriter writer = rdbmsService.createWriter(0);
    final var batchOperation =
        createAndSaveBatchOperation(
            writer,
            b ->
                b.state(BatchOperationState.ACTIVE)
                    .endDate(null)
                    .operationsTotalCount(0)
                    .operationsFailedCount(0));

    final var items =
        createAndSaveRandomBatchOperationItems(writer, batchOperation.batchOperationKey(), 2);

    // when
    writer
        .getBatchOperationWriter()
        .updateItem(
            new BatchOperationItemDbModel(
                batchOperation.batchOperationKey(),
                items.getFirst().itemKey(),
                items.getFirst().processInstanceKey(),
                BatchOperationItemState.SKIPPED,
                NOW,
                null));
    writer.flush();

    // then
    final var updatedBatchOperation = getBatchOperation(rdbmsService, batchOperation);

    assertThat(updatedBatchOperation).isNotNull();
    final BatchOperationEntity batchOperationEntity = updatedBatchOperation.items().getFirst();
    assertThat(batchOperationEntity.endDate()).isNull();
    assertThat(batchOperationEntity.operationsTotalCount()).isEqualTo(2);
    assertThat(batchOperationEntity.operationsCompletedCount()).isEqualTo(1);
    assertThat(batchOperationEntity.operationsFailedCount()).isEqualTo(0);
    assertThat(batchOperationEntity.state()).isEqualTo(BatchOperationState.ACTIVE);

    // and items have correct state
    final var updatedItems =
        getBatchOperationItems(rdbmsService, batchOperation.batchOperationKey()).items();
    assertThat(updatedItems).isNotNull();
    assertThat(updatedItems).hasSize(2);
    final var firstItem =
        updatedItems.stream()
            .filter(i -> Objects.equals(i.itemKey(), items.getFirst().itemKey()))
            .findFirst()
            .orElseThrow();
    assertThat(firstItem.state()).isEqualTo(BatchOperationItemState.SKIPPED);
    assertThat(firstItem.processedDate())
        .isCloseTo(NOW, new TemporalUnitWithinOffset(1, ChronoUnit.MILLIS));

    final var lastItem =
        updatedItems.stream()
            .filter(i -> Objects.equals(i.itemKey(), items.getLast().itemKey()))
            .findFirst()
            .orElseThrow();
    assertThat(lastItem.state()).isEqualTo(BatchOperationItemState.ACTIVE);
  }

  @TestTemplate
  public void shouldCancelBatchOperationAndActiveItems(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    // given
    final RdbmsWriter writer = rdbmsService.createWriter(0);
    final var batchOperation =
        createAndSaveBatchOperation(writer, b -> b.state(BatchOperationState.ACTIVE).endDate(null));

    final var items =
        createAndSaveRandomBatchOperationItems(writer, batchOperation.batchOperationKey(), 2);

    writer
        .getBatchOperationWriter()
        .updateItem(
            new BatchOperationItemDbModel(
                batchOperation.batchOperationKey(),
                items.getFirst().itemKey(),
                items.getFirst().processInstanceKey(),
                BatchOperationItemState.COMPLETED,
                NOW,
                null));

    // when
    final OffsetDateTime endDate = OffsetDateTime.now();
    writer.getBatchOperationWriter().cancel(batchOperation.batchOperationKey(), endDate);
    writer.flush();

    // then
    final var updatedBatchOperation = getBatchOperation(rdbmsService, batchOperation);

    // batch is canceled
    assertThat(updatedBatchOperation).isNotNull();
    assertThat(updatedBatchOperation.items().getFirst().endDate())
        .isCloseTo(endDate, new TemporalUnitWithinOffset(1, ChronoUnit.MILLIS));
    assertThat(updatedBatchOperation.items().getFirst().state())
        .isEqualTo(BatchOperationState.CANCELED);
  }

  @TestTemplate
  public void shouldSuspendBatchOperation(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    // given
    final RdbmsWriter writer = rdbmsService.createWriter(0);
    final var batchOperation =
        createAndSaveBatchOperation(writer, b -> b.state(BatchOperationState.ACTIVE).endDate(null));

    createAndSaveRandomBatchOperationItems(writer, batchOperation.batchOperationKey(), 2);

    // when
    writer.getBatchOperationWriter().suspend(batchOperation.batchOperationKey());
    writer.flush();

    // then
    final var updatedBatchOperation = getBatchOperation(rdbmsService, batchOperation);

    assertThat(updatedBatchOperation).isNotNull();
    assertThat(updatedBatchOperation.items().getFirst().endDate()).isNull();
    assertThat(updatedBatchOperation.items().getFirst().state())
        .isEqualTo(BatchOperationState.SUSPENDED);
  }

  @TestTemplate
  public void shouldResumeBatchOperation(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    // given
    final RdbmsWriter writer = rdbmsService.createWriter(0);
    final var batchOperation =
        createAndSaveBatchOperation(writer, b -> b.state(BatchOperationState.ACTIVE).endDate(null));

    createAndSaveRandomBatchOperationItems(writer, batchOperation.batchOperationKey(), 2);

    writer.getBatchOperationWriter().suspend(batchOperation.batchOperationKey());
    writer.flush();

    // when
    writer.getBatchOperationWriter().resume(batchOperation.batchOperationKey());
    writer.flush();

    // then
    final var updatedBatchOperation = getBatchOperation(rdbmsService, batchOperation);

    assertThat(updatedBatchOperation).isNotNull();
    assertThat(updatedBatchOperation.items().getFirst().endDate()).isNull();
    assertThat(updatedBatchOperation.items().getFirst().state())
        .isEqualTo(BatchOperationState.ACTIVE);
  }

  @TestTemplate
  public void shouldFinishBatchOperation(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    // given
    final RdbmsWriter writer = rdbmsService.createWriter(0);
    final var batchOperation =
        createAndSaveBatchOperation(writer, b -> b.state(BatchOperationState.ACTIVE).endDate(null));

    createAndSaveRandomBatchOperationItems(writer, batchOperation.batchOperationKey(), 2);

    // when
    final OffsetDateTime endDate = OffsetDateTime.now();
    writer.getBatchOperationWriter().finish(batchOperation.batchOperationKey(), endDate);
    writer.flush();

    // then
    final var updatedBatchOperation = getBatchOperation(rdbmsService, batchOperation);

    assertThat(updatedBatchOperation).isNotNull();
    assertThat(updatedBatchOperation.items().getFirst().endDate())
        .isCloseTo(endDate, new TemporalUnitWithinOffset(1, ChronoUnit.MILLIS));
    assertThat(updatedBatchOperation.items().getFirst().state())
        .isEqualTo(BatchOperationState.COMPLETED);
  }

  @TestTemplate
  public void shouldFinishBatchOperationWithErrors(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    // given
    final RdbmsWriter writer = rdbmsService.createWriter(0);
    final var batchOperation =
        createAndSaveBatchOperation(writer, b -> b.state(BatchOperationState.ACTIVE).endDate(null));

    final String batchOperationKey = batchOperation.batchOperationKey();
    createAndSaveRandomBatchOperationItems(writer, batchOperationKey, 2);

    final var errors =
        new BatchOperationErrorsDto(
            batchOperationKey,
            List.of(
                new BatchOperationErrorDto(1, "QUERY_FAILED", "error message 1"),
                new BatchOperationErrorDto(2, "QUERY_FAILED", "error message 2")));

    // when
    final OffsetDateTime endDate = OffsetDateTime.now();
    writer.getBatchOperationWriter().finishWithErrors(batchOperationKey, endDate, errors);
    writer.flush();

    // then
    final var updatedBatchOperation = getBatchOperation(rdbmsService, batchOperation);

    assertThat(updatedBatchOperation).isNotNull();
    final BatchOperationEntity batchOperationEntity = updatedBatchOperation.items().getFirst();
    assertThat(batchOperationEntity.endDate())
        .isCloseTo(endDate, new TemporalUnitWithinOffset(1, ChronoUnit.MILLIS));
    assertThat(batchOperationEntity.state()).isEqualTo(BatchOperationState.PARTIALLY_COMPLETED);
    assertThat(batchOperationEntity.errors()).isNotNull();
    assertThat(batchOperationEntity.errors()).hasSize(2);
    final var error1 =
        batchOperationEntity.errors().stream()
            .filter(e -> e.partitionId() == 1)
            .findFirst()
            .orElseThrow();
    assertThat(error1.partitionId()).isEqualTo(1);
    assertThat(error1.type()).isEqualTo("QUERY_FAILED");
    assertThat(error1.message()).isEqualTo("error message 1");
    final var error2 =
        batchOperationEntity.errors().stream()
            .filter(e -> e.partitionId() == 2)
            .findFirst()
            .orElseThrow();
    assertThat(error2.partitionId()).isEqualTo(2);
    assertThat(error2.type()).isEqualTo("QUERY_FAILED");
    assertThat(error2.message()).isEqualTo("error message 2");
  }

  @TestTemplate
  public void shouldFinishBatchOperationWithErrorWithLargeMessage(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    // given
    final RdbmsWriter writer = rdbmsService.createWriter(0);
    final var batchOperation =
        createAndSaveBatchOperation(writer, b -> b.state(BatchOperationState.ACTIVE).endDate(null));

    final String batchOperationKey = batchOperation.batchOperationKey();
    createAndSaveRandomBatchOperationItems(writer, batchOperationKey, 2);

    final var errors =
        new BatchOperationErrorsDto(
            batchOperationKey,
            List.of(new BatchOperationErrorDto(1, "QUERY_FAILED", "x".repeat(9000))));

    // when
    final OffsetDateTime endDate = OffsetDateTime.now();
    writer.getBatchOperationWriter().finishWithErrors(batchOperationKey, endDate, errors);
    writer.flush();

    // then
    final var updatedBatchOperation = getBatchOperation(rdbmsService, batchOperation);

    assertThat(updatedBatchOperation).isNotNull();
    final BatchOperationEntity batchOperationEntity = updatedBatchOperation.items().getFirst();
    assertThat(batchOperationEntity.errors()).isNotNull();
    assertThat(batchOperationEntity.errors()).hasSize(1);
    final var error =
        batchOperationEntity.errors().stream()
            .filter(e -> e.partitionId() == 1)
            .findFirst()
            .orElseThrow();
    assertThat(error.message().length()).isEqualTo(4000);
  }

  @TestTemplate
  public void shouldFindBatchOperationByKey(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final var batchOperation =
        createAndSaveRandomBatchOperations(rdbmsService.createWriter(0), b -> b).getLast();

    final var searchResult =
        rdbmsService
            .getBatchOperationReader()
            .search(
                new BatchOperationQuery(
                    new BatchOperationFilter.Builder()
                        .batchOperationKeys(batchOperation.batchOperationKey())
                        .build(),
                    BatchOperationSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(10))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertBatchOperationEntity(searchResult.items().getFirst(), batchOperation);
  }

  @TestTemplate
  public void shouldFindBatchOperationByOperationType(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final var batchOperation =
        createAndSaveRandomBatchOperations(rdbmsService.createWriter(0), b -> b).getLast();

    final var searchResult =
        rdbmsService
            .getBatchOperationReader()
            .search(
                new BatchOperationQuery(
                    new BatchOperationFilter.Builder()
                        .operationTypes(batchOperation.operationType().name())
                        .build(),
                    BatchOperationSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(10))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isGreaterThanOrEqualTo(1);
    assertThat(searchResult.items().size()).isGreaterThanOrEqualTo(1);
    final var operationTypes =
        searchResult.items().stream()
            .map(BatchOperationEntity::operationType)
            .collect(Collectors.toSet());
    assertThat(operationTypes).containsOnly(batchOperation.operationType());
  }

  @TestTemplate
  public void shouldFindBatchOperationByState(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    createAndSaveRandomBatchOperations(
        rdbmsService.createWriter(0), b -> b.state(BatchOperationState.COMPLETED));
    final var batchOperation =
        createAndSaveBatchOperation(
            rdbmsService.createWriter(0), b -> b.state(BatchOperationState.ACTIVE));

    final var searchResult =
        rdbmsService
            .getBatchOperationReader()
            .search(
                new BatchOperationQuery(
                    new BatchOperationFilter.Builder()
                        .states(BatchOperationState.ACTIVE.name())
                        .build(),
                    BatchOperationSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(10))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.items()).isNotEmpty();
    assertThat(searchResult.items())
        .allSatisfy(i -> assertThat(i.state()).isEqualTo(BatchOperationState.ACTIVE));
    assertThat(searchResult.items()).anySatisfy(i -> assertBatchOperationEntity(i, batchOperation));
  }

  @TestTemplate
  public void shouldFindBatchOperationWithFullFilter(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    createAndSaveRandomBatchOperations(
        rdbmsService.createWriter(0), b -> b.state(BatchOperationState.COMPLETED));
    final var batchOperation =
        createAndSaveBatchOperation(
            rdbmsService.createWriter(0), b -> b.state(BatchOperationState.ACTIVE));

    final var searchResult =
        rdbmsService
            .getBatchOperationReader()
            .search(
                new BatchOperationQuery(
                    new BatchOperationFilter.Builder()
                        .batchOperationKeys(batchOperation.batchOperationKey())
                        .operationTypes(batchOperation.operationType().name())
                        .states(batchOperation.state().name())
                        .build(),
                    BatchOperationSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(10))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.items()).isNotEmpty();
    assertThat(searchResult.items())
        .allSatisfy(i -> assertThat(i.state()).isEqualTo(BatchOperationState.ACTIVE));
    assertThat(searchResult.items()).anySatisfy(i -> assertBatchOperationEntity(i, batchOperation));
  }

  @TestTemplate
  public void shouldFindAllBatchOperationsPaged(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final BatchOperationType operationType = randomEnum(BatchOperationType.class);
    createAndSaveRandomBatchOperations(
        rdbmsService.createWriter(0), b -> b.operationType(operationType));

    final var searchResult =
        rdbmsService
            .getBatchOperationReader()
            .search(
                new BatchOperationQuery(
                    new BatchOperationFilter.Builder().operationTypes(operationType.name()).build(),
                    BatchOperationSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(20);
    assertThat(searchResult.items()).hasSize(5);
  }

  @TestTemplate
  public void shouldFindAllBatchOperationItemsPaged(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter writer = rdbmsService.createWriter(0);

    final var batchOperation = createAndSaveBatchOperation(writer, b -> b);

    final var items =
        createAndSaveRandomBatchOperationItems(writer, batchOperation.batchOperationKey(), 10);

    final var searchResult =
        rdbmsService
            .getBatchOperationItemReader()
            .search(
                new BatchOperationItemQuery(
                    new BatchOperationItemFilter.Builder()
                        .batchOperationKeys(batchOperation.batchOperationKey())
                        .build(),
                    BatchOperationItemSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(10);
    assertThat(searchResult.items()).hasSize(5);
  }

  @TestTemplate
  public void shouldFindBatchOperationItemsWithFullFilter(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter writer = rdbmsService.createWriter(0);

    final var batchOperation =
        createAndSaveBatchOperation(writer, b -> b.state(BatchOperationState.ACTIVE).endDate(null));

    final var items =
        createSaveReturnRandomBatchOperationItems(writer, batchOperation.batchOperationKey(), 10);

    final var itemKeys =
        items.stream().map(BatchOperationItemDbModel::itemKey).collect(Collectors.toList());

    final var processInstanceKeys =
        items.stream()
            .map(BatchOperationItemDbModel::processInstanceKey)
            .collect(Collectors.toList());

    final var searchResult =
        rdbmsService
            .getBatchOperationItemReader()
            .search(
                new BatchOperationItemQuery(
                    new BatchOperationItemFilter.Builder()
                        .batchOperationKeys(batchOperation.batchOperationKey())
                        .itemKeyOperations(mapDefaultToOperation(itemKeys))
                        .processInstanceKeyOperations(mapDefaultToOperation(processInstanceKeys))
                        .states(BatchOperationState.ACTIVE.name())
                        .build(),
                    BatchOperationItemSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(10);
    assertThat(searchResult.items()).hasSize(5);
  }

  private static void assertBatchOperationEntity(
      final BatchOperationEntity instance, final BatchOperationDbModel batchOperation) {
    assertThat(instance).isNotNull();
    assertThat(instance)
        .usingRecursiveComparison()
        .ignoringFields("batchOperationKey", "startDate", "endDate")
        .isEqualTo(batchOperation);
    assertThat(instance.batchOperationKey()).isEqualTo(batchOperation.batchOperationKey());
    assertThat(instance.startDate())
        .isCloseTo(batchOperation.startDate(), new TemporalUnitWithinOffset(1, ChronoUnit.MILLIS));
    assertThat(instance.endDate())
        .isCloseTo(batchOperation.endDate(), new TemporalUnitWithinOffset(1, ChronoUnit.MILLIS));
  }

  private static SearchQueryResult<BatchOperationEntity> getBatchOperation(
      final RdbmsService rdbmsService, final BatchOperationDbModel batchOperation) {
    return rdbmsService
        .getBatchOperationReader()
        .search(
            new BatchOperationQuery(
                new BatchOperationFilter.Builder()
                    .batchOperationKeys(batchOperation.batchOperationKey())
                    .build(),
                BatchOperationSort.of(b -> b),
                SearchQueryPage.of(b -> b)));
  }

  private static SearchQueryResult<BatchOperationItemEntity> getBatchOperationItems(
      final RdbmsService rdbmsService, final String batchOperationKey) {
    return rdbmsService
        .getBatchOperationItemReader()
        .search(
            new BatchOperationItemQuery(
                new BatchOperationItemFilter.Builder()
                    .batchOperationKeys(batchOperationKey)
                    .build(),
                BatchOperationItemSort.of(b -> b),
                SearchQueryPage.of(b -> b)));
  }
}
