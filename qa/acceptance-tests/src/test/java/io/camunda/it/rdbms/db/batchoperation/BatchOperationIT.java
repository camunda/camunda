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
import static io.camunda.it.rdbms.db.fixtures.BatchOperationFixtures.insertBatchOperationsItems;
import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.nextKey;
import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.nextStringId;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.db.rdbms.write.domain.BatchOperationDbModel;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.BatchOperationEntity;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationItemEntity;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationItemState;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationState;
import io.camunda.search.filter.BatchOperationFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.BatchOperationQuery;
import io.camunda.search.query.SearchQueryResult;
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

    final var searchResult = rdbmsService.getBatchOperationReader().exists(nextKey());

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
        rdbmsService.getBatchOperationReader().getItems(batchOperation.batchOperationKey());
    assertThat(updatedItems).isNotNull();
    assertThat(updatedItems).hasSize(3);
    assertThat(updatedItems.stream().map(BatchOperationItemEntity::state))
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

    final List<Long> items =
        createAndSaveRandomBatchOperationItems(writer, batchOperation.batchOperationKey(), 2);

    // when
    writer
        .getBatchOperationWriter()
        .updateItem(
            batchOperation.batchOperationKey(),
            items.getFirst(),
            BatchOperationItemState.COMPLETED);
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
        rdbmsService.getBatchOperationReader().getItems(batchOperation.batchOperationKey());
    assertThat(updatedItems).isNotNull();
    assertThat(updatedItems).hasSize(2);
    final var firstItem =
        updatedItems.stream()
            .filter(i -> Objects.equals(i.itemKey(), items.getFirst()))
            .findFirst()
            .get();
    assertThat(firstItem.state()).isEqualTo(BatchOperationItemState.COMPLETED);

    final var lastItem =
        updatedItems.stream()
            .filter(i -> Objects.equals(i.itemKey(), items.getLast()))
            .findFirst()
            .get();
    assertThat(lastItem.state()).isEqualTo(BatchOperationItemState.ACTIVE);
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

    final List<Long> items =
        createAndSaveRandomBatchOperationItems(writer, batchOperation.batchOperationKey(), 2);

    // when
    writer
        .getBatchOperationWriter()
        .updateItem(
            batchOperation.batchOperationKey(), items.getFirst(), BatchOperationItemState.FAILED);
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
        rdbmsService.getBatchOperationReader().getItems(batchOperation.batchOperationKey());
    assertThat(updatedItems).isNotNull();
    assertThat(updatedItems).hasSize(2);
    final var firstItem =
        updatedItems.stream()
            .filter(i -> Objects.equals(i.itemKey(), items.getFirst()))
            .findFirst()
            .get();
    assertThat(firstItem.state()).isEqualTo(BatchOperationItemState.FAILED);

    final var lastItem =
        updatedItems.stream()
            .filter(i -> Objects.equals(i.itemKey(), items.getLast()))
            .findFirst()
            .get();
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
            batchOperation.batchOperationKey(),
            items.getFirst(),
            BatchOperationItemState.COMPLETED);

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
  public void shouldPauseBatchOperation(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    // given
    final RdbmsWriter writer = rdbmsService.createWriter(0);
    final var batchOperation =
        createAndSaveBatchOperation(writer, b -> b.state(BatchOperationState.ACTIVE).endDate(null));

    createAndSaveRandomBatchOperationItems(writer, batchOperation.batchOperationKey(), 2);

    // when
    writer.getBatchOperationWriter().paused(batchOperation.batchOperationKey());
    writer.flush();

    // then
    final var updatedBatchOperation = getBatchOperation(rdbmsService, batchOperation);

    assertThat(updatedBatchOperation).isNotNull();
    assertThat(updatedBatchOperation.items().getFirst().endDate()).isNull();
    assertThat(updatedBatchOperation.items().getFirst().state())
        .isEqualTo(BatchOperationState.PAUSED);
  }

  @TestTemplate
  public void shouldResumeBatchOperation(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    // given
    final RdbmsWriter writer = rdbmsService.createWriter(0);
    final var batchOperation =
        createAndSaveBatchOperation(writer, b -> b.state(BatchOperationState.ACTIVE).endDate(null));

    createAndSaveRandomBatchOperationItems(writer, batchOperation.batchOperationKey(), 2);

    writer.getBatchOperationWriter().paused(batchOperation.batchOperationKey());
    writer.flush();

    // when
    writer.getBatchOperationWriter().resumed(batchOperation.batchOperationKey());
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
                        .operationTypes(batchOperation.operationType())
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
                        .state(BatchOperationState.ACTIVE.name())
                        .build(),
                    BatchOperationSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(10))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertBatchOperationEntity(searchResult.items().getFirst(), batchOperation);
  }

  @TestTemplate
  public void shouldFindAllBatchOperationsPaged(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final String operationType = nextStringId();
    createAndSaveRandomBatchOperations(
        rdbmsService.createWriter(0), b -> b.operationType(operationType));

    final var searchResult =
        rdbmsService
            .getBatchOperationReader()
            .search(
                new BatchOperationQuery(
                    new BatchOperationFilter.Builder().operationTypes(operationType).build(),
                    BatchOperationSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(20);
    assertThat(searchResult.items()).hasSize(5);
  }

  private static void assertBatchOperationEntity(
      final BatchOperationEntity instance, final BatchOperationDbModel batchOperation) {
    assertThat(instance).isNotNull();
    assertThat(instance)
        .usingRecursiveComparison()
        .ignoringFields("startDate", "endDate")
        .isEqualTo(batchOperation);
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
}
