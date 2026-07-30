/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.migration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.schema.PrefixMigrationClient;
import io.camunda.search.schema.utils.CloneResult;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class PrefixMigrationHelperTest {

  @Test
  void shouldReturnEmptyBatchesForEmptyInput() {
    // when
    final var batches = PrefixMigrationHelper.partitionIntoBatches(List.of(), 100);

    // then
    assertThat(batches).isEmpty();
  }

  @Test
  void shouldReturnSingleBatchWhenAllIndicesFit() {
    // given
    final var indices = List.of("index-a", "index-b", "index-c");

    // when
    final var batches = PrefixMigrationHelper.partitionIntoBatches(indices, 100);

    // then
    assertThat(batches).hasSize(1);
    assertThat(batches.getFirst()).containsExactlyElementsOf(indices);
  }

  @Test
  void shouldSplitIntoMultipleBatchesWhenLimitExceeded() {
    // given
    // "index-a" = 7, "index-b" = 7, combined with comma = 15 -> exceeds 10
    final var indices = List.of("index-a", "index-b", "index-c");

    // when
    final var batches = PrefixMigrationHelper.partitionIntoBatches(indices, 10);

    // then
    assertThat(batches).hasSize(3);
    assertThat(batches.get(0)).containsExactly("index-a");
    assertThat(batches.get(1)).containsExactly("index-b");
    assertThat(batches.get(2)).containsExactly("index-c");
  }

  @Test
  void shouldGroupIndicesThatFitTogether() {
    // given
    // "ab" = 2, "cd" = 2, "ab,cd" = 5, fits in 5
    // "ef" = 2, starts a new batch
    final var indices = List.of("ab", "cd", "ef");

    // when
    final var batches = PrefixMigrationHelper.partitionIntoBatches(indices, 5);

    // then
    assertThat(batches).hasSize(2);
    assertThat(batches.get(0)).containsExactly("ab", "cd");
    assertThat(batches.get(1)).containsExactly("ef");
  }

  @Test
  void shouldSkipIndicesThatExceedMaxLengthOnTheirOwn() {
    // given
    final var longIndex = "a".repeat(200);
    final var indices = List.of("index-a", longIndex, "index-b");

    // when
    final var batches = PrefixMigrationHelper.partitionIntoBatches(indices, 100);

    // then
    assertThat(batches).hasSize(1);
    assertThat(batches.getFirst()).containsExactly("index-a", "index-b");
  }

  @Test
  void shouldHandleManyIndicesInBatches() {
    // given
    final var indices = IntStream.range(0, 100).mapToObj(i -> "index-%03d".formatted(i)).toList();

    // when
    // each index name is 9 chars; 10 per batch: 10*9 + 9 commas = 99 chars total
    final var batches = PrefixMigrationHelper.partitionIntoBatches(indices, 99);

    // then
    assertThat(batches).hasSize(10);
    batches.forEach(batch -> assertThat(batch).hasSize(10));
  }

  @Test
  void shouldReturnEmptyBatchesWhenAllIndicesExceedLimit() {
    // given
    final var indices = List.of("a".repeat(50), "b".repeat(50));

    // when
    final var batches = PrefixMigrationHelper.partitionIntoBatches(indices, 10);

    // then
    assertThat(batches).isEmpty();
  }

  @Test
  void shouldHandleSingleIndexThatFitsExactly() {
    // given
    final var index = "index-exact";

    // when
    final var batches =
        PrefixMigrationHelper.partitionIntoBatches(
            Collections.singletonList(index), index.length());

    // then
    assertThat(batches).hasSize(1);
    assertThat(batches.getFirst()).containsExactly(index);
  }

  @Test
  void shouldReportAllCloneSucceededWhenNoFailures() {
    // given
    final var results =
        List.of(
            new CloneResult(true, "source-a", "dest-a", null),
            new CloneResult(true, "source-b", "dest-b", null));

    // when
    final var allSucceeded = PrefixMigrationHelper.logResultsAndCheckAllSucceeded(results);

    // then
    assertThat(allSucceeded).isTrue();
  }

  @Test
  void shouldReportCloneFailureWhenAnyResultFailed() {
    // given
    final var results =
        List.of(
            new CloneResult(true, "source-a", "dest-a", null),
            new CloneResult(false, "source-b", "dest-b", new RuntimeException("clone failed")));

    // when
    final var allSucceeded = PrefixMigrationHelper.logResultsAndCheckAllSucceeded(results);

    // then
    assertThat(allSucceeded).isFalse();
  }

  @Test
  void shouldReportFailureWhenAllClonesFailed() {
    // given
    final var results =
        List.of(
            new CloneResult(false, "source-a", "dest-a", new RuntimeException("boom")),
            new CloneResult(false, "source-b", "dest-b", new RuntimeException("boom")));

    // when
    final var allSucceeded = PrefixMigrationHelper.logResultsAndCheckAllSucceeded(results);

    // then
    assertThat(allSucceeded).isFalse();
  }

  @Test
  void shouldReportSuccessForEmptyResults() {
    // when
    final var allSucceeded = PrefixMigrationHelper.logResultsAndCheckAllSucceeded(List.of());

    // then
    assertThat(allSucceeded).isTrue();
  }

  @Test
  void shouldFlattenCloneResultsAcrossAllAliasGroups() {
    // given
    final var expectedTotal =
        PrefixMigrationHelper.TASKLIST_INDICES_TO_MIGRATE.size()
            + PrefixMigrationHelper.OPERATE_INDICES_TO_MIGRATE.size();
    final var descriptors = new IndexDescriptors("camunda", true);
    final var client = new FakePrefixMigrationClient(Set.of());
    final var executor = Executors.newVirtualThreadPerTaskExecutor();

    try {
      // when
      final var results =
          PrefixMigrationHelper.migrate(
                  "operate-dev", "tasklist-dev", "camunda", true, descriptors, client, executor)
              .get()
              .join();

      // then
      assertThat(results).hasSize(expectedTotal);
      assertThat(results).allMatch(CloneResult::successful);
    } finally {
      executor.close();
    }
  }

  @Test
  void shouldFlattenMixedSuccessAndFailureCloneResultsAcrossAliasGroups() {
    // given - only the ProcessIndex alias group is made to fail
    final var expectedTotal =
        PrefixMigrationHelper.TASKLIST_INDICES_TO_MIGRATE.size()
            + PrefixMigrationHelper.OPERATE_INDICES_TO_MIGRATE.size();
    final var descriptors = new IndexDescriptors("camunda", true);
    final var client = new FakePrefixMigrationClient(Set.of("process"));
    final var executor = Executors.newVirtualThreadPerTaskExecutor();

    try {
      // when
      final var results =
          PrefixMigrationHelper.migrate(
                  "operate-dev", "tasklist-dev", "camunda", true, descriptors, client, executor)
              .get()
              .join();

      // then
      assertThat(results).hasSize(expectedTotal);
      assertThat(results.stream().filter(result -> !result.successful())).hasSize(1);
    } finally {
      executor.close();
    }
  }

  /**
   * Fake {@link PrefixMigrationClient} that reports exactly one index per queried alias and
   * completes {@code cloneAndDeleteIndex} synchronously, failing only for aliases containing one of
   * the given substrings - used to exercise {@link PrefixMigrationHelper#migrate} without a real
   * Elasticsearch/OpenSearch client.
   */
  private static final class FakePrefixMigrationClient implements PrefixMigrationClient {
    private final Set<String> failingAliasSubstrings;

    private FakePrefixMigrationClient(final Set<String> failingAliasSubstrings) {
      this.failingAliasSubstrings = failingAliasSubstrings;
    }

    @Override
    public List<String> getIndicesInAlias(final String alias) {
      return List.of(alias.replace("_alias", "_index"));
    }

    @Override
    public CompletableFuture<CloneResult> cloneAndDeleteIndex(
        final String source,
        final String sourceAlias,
        final String destination,
        final String destinationAlias) {
      final var shouldFail = failingAliasSubstrings.stream().anyMatch(sourceAlias::contains);
      return CompletableFuture.completedFuture(
          shouldFail
              ? new CloneResult(
                  false, source, destination, new RuntimeException("simulated failure"))
              : new CloneResult(true, source, destination, null));
    }

    @Override
    public CompletableFuture<Void> deleteIndex(final String... indexName) {
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> deleteComponentTemplate(final String componentTemplateName) {
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> deleteIndexTemplate(final String indexTemplateName) {
      return CompletableFuture.completedFuture(null);
    }
  }
}
