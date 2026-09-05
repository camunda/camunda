/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.exporter.ExporterMetadata;
import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.search.test.utils.TestObjectMapper;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class PendingIncidentUpdateCountTaskTest {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(PendingIncidentUpdateCountTaskTest.class);
  private static final Duration TIMEOUT = Duration.ofSeconds(5);

  private final ExporterMetadata metadata = new ExporterMetadata(TestObjectMapper.objectMapper());
  private final IncidentUpdateRepository repository = Mockito.mock(IncidentUpdateRepository.class);
  private final CamundaExporterMetrics metrics = Mockito.mock(CamundaExporterMetrics.class);

  private PendingIncidentUpdateCountTask task;

  @BeforeEach
  void setUp() {
    task = new PendingIncidentUpdateCountTask(metrics, metadata, repository, LOGGER);
  }

  @Test
  void shouldUpdateMetricsWithPendingIncidentCount() {
    // given
    when(repository.getCountOfPendingIncidentUpdates(anyLong()))
        .thenReturn(CompletableFuture.completedFuture(1234));

    // when
    final var res = task.execute();

    // then
    assertThat(res).succeedsWithin(TIMEOUT).isEqualTo(1234);

    verify(metrics).setPendingIncidentUpdatesCount(1234);
  }

  @Test
  void shouldNotUpdateMetricsWhenTaskFails() {
    // given
    when(repository.getCountOfPendingIncidentUpdates(anyLong()))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("test exception")));

    // when
    final var res = task.execute();

    // then
    assertThat(res).failsWithin(TIMEOUT);

    verifyNoInteractions(metrics);
  }
}
