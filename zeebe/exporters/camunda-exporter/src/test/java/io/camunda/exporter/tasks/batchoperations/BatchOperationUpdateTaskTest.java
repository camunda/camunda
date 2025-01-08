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
import io.camunda.exporter.tasks.batchoperations.BatchOperationUpdateRepository.OperationsAggData;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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
  void shouldReturnZeroIfNoDocumentUpdatesRequired() {
    // given - when
    repository.batchOperationIds.add("1");
    final var result = task.execute();

    // then
    assertThat(result)
        .succeedsWithin(REQUEST_TIMEOUT)
        .asInstanceOf(InstanceOfAssertFactories.type(Integer.class))
        .isEqualTo(0);
  }

  @Test
  void shouldUpdateBatchOperations() {
    // given - when
    repository.batchOperationIds.add("1");
    repository.batchOperationIds.add("2");
    repository.batchOperationIds.add("3");
    repository.finishedOperationsCount.add(new OperationsAggData("1", 5));
    repository.finishedOperationsCount.add(new OperationsAggData("2", 6));
    final var result = task.execute();

    // then
    assertThat(result)
        .succeedsWithin(REQUEST_TIMEOUT)
        .asInstanceOf(InstanceOfAssertFactories.type(Integer.class))
        .isEqualTo(2);
    assertThat(repository.documentUpdates).hasSize(2);
    assertThat(repository.documentUpdates)
        .contains(new DocumentUpdate("1", 5L), new DocumentUpdate("2", 6L));
  }

  private static final class TestRepository implements BatchOperationUpdateRepository {
    List<String> batchOperationIds = new ArrayList<>();
    List<OperationsAggData> finishedOperationsCount = new ArrayList<>();
    private List<DocumentUpdate> documentUpdates = new ArrayList<>();

    @Override
    public CompletionStage<Collection<String>> getNotFinishedBatchOperations() {
      return CompletableFuture.completedFuture(batchOperationIds);
    }

    @Override
    public CompletionStage<List<OperationsAggData>> getFinishedOperationsCount(
        final Collection<String> batchOperationIds) {
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
