/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.batchoperations;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.exporter.tasks.batchoperations.BatchOperationUpdateRepository.DocumentUpdate;
import io.camunda.exporter.tasks.batchoperations.BatchOperationUpdateRepository.NotFinishedBatchOperation;
import io.camunda.exporter.tasks.batchoperations.BatchOperationUpdateRepository.OperationsAggData;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity.BatchOperationState;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchOperationUpdateTaskTest {
  private static final Logger LOGGER = LoggerFactory.getLogger(BatchOperationUpdateTaskTest.class);
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
  private final TestRepository repository = Mockito.spy(new TestRepository());
  private final BatchOperationUpdateTask task =
      new BatchOperationUpdateTask(repository, LOGGER, Runnable::run);

  @Test
  void shouldReturnZeroIfNoBatchOperationsFound() {
    // given - when
    final var result = task.execute();

    // then
    assertThat(result)
        .succeedsWithin(REQUEST_TIMEOUT)
        .asInstanceOf(InstanceOfAssertFactories.type(Integer.class))
        .isEqualTo(0);
  }

  @Test
  void shouldUpdateCompletedBatchOperationWithoutItemsWithZeroCounts() {
    // given a completed batch operation whose item query matched nothing, so it has no items and no
    // single operations
    repository.batchOperations.add(
        new NotFinishedBatchOperation("1", BatchOperationState.COMPLETED, 0));

    // when
    final var result = task.execute();

    // then it is still updated, which is what sets its end date
    assertThat(result)
        .succeedsWithin(REQUEST_TIMEOUT)
        .asInstanceOf(InstanceOfAssertFactories.type(Integer.class))
        .isEqualTo(1);
    assertThat(repository.documentUpdates).containsExactly(new DocumentUpdate("1", 0L, 0L, 0L, 0L));
  }

  @Test
  void shouldNotUpdateRunningBatchOperationWithoutSingleOperations() {
    // given a running batch operation whose single operations are not exported yet
    repository.batchOperations.add(
        new NotFinishedBatchOperation("1", BatchOperationState.ACTIVE, 5));

    // when
    final var result = task.execute();

    // then no zeroed update is written, as it could not set an end date anyway
    assertThat(result)
        .succeedsWithin(REQUEST_TIMEOUT)
        .asInstanceOf(InstanceOfAssertFactories.type(Integer.class))
        .isEqualTo(0);
    assertThat(repository.documentUpdates).isEmpty();
  }

  @Test
  void shouldNotUpdateBatchOperationWithItemsButNoSingleOperationsLeft() {
    // given a completed batch operation that had items, but whose single operations left the
    // operation index, for example because they were archived
    repository.batchOperations.add(
        new NotFinishedBatchOperation("1", BatchOperationState.COMPLETED, 5));

    // when
    final var result = task.execute();

    // then its previously written counts are left untouched instead of being zeroed
    assertThat(result)
        .succeedsWithin(REQUEST_TIMEOUT)
        .asInstanceOf(InstanceOfAssertFactories.type(Integer.class))
        .isEqualTo(0);
    assertThat(repository.documentUpdates).isEmpty();
  }

  @Test
  void shouldUpdateBatchOperations() {
    // given - when
    repository.batchOperations.add(
        new NotFinishedBatchOperation("1", BatchOperationState.ACTIVE, 5));
    repository.batchOperations.add(
        new NotFinishedBatchOperation("2", BatchOperationState.ACTIVE, 6));
    repository.batchOperations.add(
        new NotFinishedBatchOperation("3", BatchOperationState.COMPLETED, 0));
    repository.batchOperations.add(
        new NotFinishedBatchOperation("4", BatchOperationState.ACTIVE, 7));
    repository.finishedOperationsCount.add(new OperationsAggData("1", Map.of("COMPLETED", 5L)));
    repository.finishedOperationsCount.add(new OperationsAggData("2", Map.of("COMPLETED", 6L)));
    final var result = task.execute();

    // then batch operations with single operations and those completed without items are updated,
    // while the one whose counts are simply unknown is skipped
    assertThat(result)
        .succeedsWithin(REQUEST_TIMEOUT)
        .asInstanceOf(InstanceOfAssertFactories.type(Integer.class))
        .isEqualTo(3);
    assertThat(repository.documentUpdates)
        .containsExactlyInAnyOrder(
            new DocumentUpdate("1", 5L, 0L, 5L, 5L),
            new DocumentUpdate("2", 6L, 0L, 6L, 6L),
            new DocumentUpdate("3", 0L, 0L, 0L, 0L));
  }

  private static final class TestRepository implements BatchOperationUpdateRepository {
    List<NotFinishedBatchOperation> batchOperations = new ArrayList<>();
    List<OperationsAggData> finishedOperationsCount = new ArrayList<>();
    private List<DocumentUpdate> documentUpdates = new ArrayList<>();

    @Override
    public CompletionStage<Collection<NotFinishedBatchOperation>> getNotFinishedBatchOperations() {
      return CompletableFuture.completedFuture(batchOperations);
    }

    @Override
    public CompletionStage<List<OperationsAggData>> getOperationsCount(
        final Collection<String> batchOperationKeys) {
      return CompletableFuture.completedFuture(finishedOperationsCount);
    }

    @Override
    public CompletionStage<Integer> bulkUpdate(final List<DocumentUpdate> documentUpdates) {
      this.documentUpdates = documentUpdates;
      return CompletableFuture.completedFuture(documentUpdates.size());
    }

    @Override
    public void close() throws Exception {}
  }
}
