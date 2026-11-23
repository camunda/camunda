/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.sql.BatchOperationMapper;
import io.camunda.db.rdbms.sql.BatchOperationMapper.BatchOperationErrorsDto;
import io.camunda.db.rdbms.sql.BatchOperationMapper.BatchOperationFinalizeEndDateDto;
import io.camunda.db.rdbms.sql.BatchOperationMapper.BatchOperationItemCompletionDto;
import io.camunda.db.rdbms.sql.BatchOperationMapper.BatchOperationItemsDto;
import io.camunda.db.rdbms.sql.BatchOperationMapper.BatchOperationUpdateDto;
import io.camunda.db.rdbms.write.RdbmsWriterConfig;
import io.camunda.db.rdbms.write.domain.BatchOperationItemDbModel;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationItemState;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationState;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class BatchOperationWriterTest {

  private final ExecutionQueue executionQueue = Mockito.mock(ExecutionQueue.class);
  private final VendorDatabaseProperties vendorDatabaseProperties =
      Mockito.mock(VendorDatabaseProperties.class);
  private final BatchOperationMapper batchOperationMapper =
      Mockito.mock(BatchOperationMapper.class);
  private final BatchOperationWriter batchOperationWriter =
      new BatchOperationWriter(
          executionQueue,
          batchOperationMapper,
          RdbmsWriterConfig.builder().batchOperationItemInsertBlockSize(10).build(),
          vendorDatabaseProperties);

  @Test
  void testSplitCorrectly() {
    // given
    final var itemsDto =
        new BatchOperationItemsDto(
            "42",
            LongStream.range(0, 25)
                .boxed()
                .map(
                    i ->
                        new BatchOperationItemDbModel(
                            "42",
                            i,
                            i,
                            i,
                            BatchOperationItemState.ACTIVE,
                            OffsetDateTime.now(),
                            null))
                .toList());

    // when
    batchOperationWriter.insertItems(itemsDto);

    final var payloadCaptor = ArgumentCaptor.forClass(QueueItem.class);
    Mockito.verify(executionQueue, Mockito.times(3)).executeInQueue(payloadCaptor.capture());

    final var capturedItems =
        payloadCaptor.getAllValues().stream()
            .map(q -> (BatchOperationItemsDto) q.parameter())
            .toList();

    assertThat(capturedItems.get(0).items()).hasSize(10);
    assertThat(capturedItems.get(1).items()).hasSize(10);
    assertThat(capturedItems.get(2).items()).hasSize(5);
  }

  @Test
  void shouldFinalizeEndDateAfterFinish() {
    // given
    final var endDate = OffsetDateTime.now();

    // when
    batchOperationWriter.finish("42", endDate);

    // then the state is written without an end date, and the end date is derived afterwards
    final var queued = queuedItems();
    assertThat(queued).hasSize(2);
    assertThat(statement(queued, "updateCompleted").parameter())
        .isEqualTo(new BatchOperationUpdateDto("42", BatchOperationState.COMPLETED, null));
    assertFinalizedLast(queued, new BatchOperationFinalizeEndDateDto("42", endDate));
  }

  @ParameterizedTest
  @EnumSource(
      value = BatchOperationState.class,
      names = {"PARTIALLY_COMPLETED", "FAILED"})
  void shouldFinalizeEndDateAfterFinishWithErrors(final BatchOperationState state) {
    // given
    final var endDate = OffsetDateTime.now();
    final var errors = new BatchOperationErrorsDto("42", List.of());

    // when
    batchOperationWriter.finishWithErrors("42", endDate, errors, state);

    // then
    final var queued = queuedItems();
    assertThat(statement(queued, "updateCompleted").parameter())
        .isEqualTo(new BatchOperationUpdateDto("42", state, null));
    assertFinalizedLast(queued, new BatchOperationFinalizeEndDateDto("42", endDate));
  }

  @ParameterizedTest
  @EnumSource(
      value = BatchOperationItemState.class,
      names = {"COMPLETED", "SKIPPED", "FAILED"})
  void shouldFinalizeEndDateAfterItemSettles(final BatchOperationItemState itemState) {
    // given
    final var processedDate = OffsetDateTime.now();
    final var item =
        new BatchOperationItemDbModel("42", 1L, 2L, 3L, itemState, processedDate, null);

    // when
    batchOperationWriter.updateItem(item);

    // then the end date is derived at the time the item was processed
    assertFinalizedLast(queuedItems(), new BatchOperationFinalizeEndDateDto("42", processedDate));
  }

  @Test
  void shouldNotFinalizeEndDateWhileItemIsActive() {
    // given
    final var item =
        new BatchOperationItemDbModel(
            "42", 1L, 2L, 3L, BatchOperationItemState.ACTIVE, OffsetDateTime.now(), null);

    // when
    batchOperationWriter.updateItem(item);

    // then
    assertThat(queuedItems()).noneMatch(q -> q.statementId().endsWith("finalizeEndDate"));
  }

  @Test
  void shouldNotFinalizeEndDateOfCanceledBatchOperation() {
    // when
    batchOperationWriter.cancel("42", OffsetDateTime.now());

    // then the canceled batch operation keeps the end date written by the cancellation
    assertThat(queuedItems()).noneMatch(q -> q.statementId().endsWith("finalizeEndDate"));
  }

  @Test
  void shouldFinalizeEndDateOfEachBatchOperationWhenItemsAreCompletedInBulk() {
    // given
    final var processedDate = OffsetDateTime.now();
    final var items =
        List.of(
            new BatchOperationItemCompletionDto("1", 10L),
            new BatchOperationItemCompletionDto("1", 11L),
            new BatchOperationItemCompletionDto("2", 20L));

    // when
    batchOperationWriter.completeBatchOperationItems(items, processedDate);

    // then
    Mockito.verify(batchOperationMapper)
        .finalizeEndDate(new BatchOperationFinalizeEndDateDto("1", processedDate));
    Mockito.verify(batchOperationMapper)
        .finalizeEndDate(new BatchOperationFinalizeEndDateDto("2", processedDate));
  }

  private List<QueueItem> queuedItems() {
    final var captor = ArgumentCaptor.forClass(QueueItem.class);
    Mockito.verify(executionQueue, Mockito.atLeastOnce()).executeInQueue(captor.capture());
    return captor.getAllValues();
  }

  private static QueueItem statement(final List<QueueItem> queued, final String statement) {
    return queued.stream()
        .filter(q -> q.statementId().endsWith("." + statement))
        .findFirst()
        .orElseThrow();
  }

  /**
   * The queue sorts the updates of a flush by order and statement ID, so the end date has to be
   * queued with a higher order than everything else it reads.
   */
  private static void assertFinalizedLast(
      final List<QueueItem> queued, final BatchOperationFinalizeEndDateDto expected) {
    final var finalize = statement(queued, "finalizeEndDate");
    assertThat(finalize.parameter()).isEqualTo(expected);
    assertThat(queued.stream().filter(q -> q != finalize))
        .allSatisfy(other -> assertThat(finalize.order()).isGreaterThan(other.order()));
  }
}
