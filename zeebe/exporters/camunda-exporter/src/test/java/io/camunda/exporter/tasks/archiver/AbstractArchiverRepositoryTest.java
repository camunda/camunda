/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.schema.config.RetentionConfiguration;
import java.net.ConnectException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

abstract class AbstractArchiverRepositoryTest {

  final RetentionConfiguration retention = new RetentionConfiguration();
  ArchiverRepository repository;

  @BeforeEach
  void setup() {
    repository = createRepository();
  }

  @AfterEach
  void teardown() throws Exception {
    if (repository != null) {
      repository.close();
    }
  }

  @ParameterizedTest
  @MethodSource("archiveBatchSuppliers")
  void shouldReturnFailedFutureWhenGetNextBatchFails(
      final Function<ArchiverRepository, CompletableFuture<?>> archiveBatchSupplier) {
    // when
    final var result = archiveBatchSupplier.apply(repository);

    // then fails when it tries to access ES/OS, since there is no backing database
    assertThat(result)
        .as("Should return a future that completes exceptionally, not throw synchronously")
        .failsWithin(Duration.ofSeconds(5))
        .withThrowableOfType(ExecutionException.class)
        .withRootCauseInstanceOf(ConnectException.class);
  }

  static Stream<Named<Function<ArchiverRepository, CompletableFuture<?>>>> archiveBatchSuppliers() {
    return Stream.of(
        Named.of("getProcessInstancesNextBatch", ArchiverRepository::getProcessInstancesNextBatch),
        Named.of("getBatchOperationsNextBatch", ArchiverRepository::getBatchOperationsNextBatch),
        Named.of("getUsageMetricTUNextBatch", ArchiverRepository::getUsageMetricTUNextBatch),
        Named.of("getUsageMetricNextBatch", ArchiverRepository::getUsageMetricNextBatch),
        Named.of("getJobBatchMetricsNextBatch", ArchiverRepository::getJobBatchMetricsNextBatch),
        Named.of(
            "getStandaloneDecisionNextBatch", ArchiverRepository::getStandaloneDecisionNextBatch));
  }

  @Test
  void shouldNotSetLifecycleIfRetentionIsDisabled() {
    // given
    retention.setEnabled(false);

    // when
    final var result = repository.setIndexLifeCycle("whatever");

    // then - would normally fail if tried to access ES/OS, since there is no backing database
    assertThat(result)
        .as("did not try connecting to non existent ES/OS")
        .succeedsWithin(Duration.ofSeconds(5));
  }

  abstract ArchiverRepository createRepository();
}
