/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.topology;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.broker.exporter.repo.ExporterLoadException;
import io.camunda.zeebe.broker.exporter.repo.ExporterRepository;
import io.camunda.zeebe.dynamic.nodeid.NodeIdProvider;
import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class ClusterChangeExecutorImplTest {

  @Test
  void shouldRunPurgeForEveryExporter() {
    // given
    final ExporterRepository repository = new ExporterRepository();
    try {
      repository.validateAndAddExporterDescriptor("test-1", AuditExporter.class, null);
      repository.validateAndAddExporterDescriptor("test-2", AuditExporter.class, null);
    } catch (final ExporterLoadException e) {
      Assertions.fail(e);
    }

    final var executor =
        new ClusterChangeExecutorImpl(
            new TestConcurrencyControl(), repository, null, new SimpleMeterRegistry());

    // when
    final Future<Void> result = executor.deleteHistory();

    // then
    Assertions.assertThat(result).succeedsWithin(Duration.ofSeconds(5));
    Assertions.assertThat(AuditExporter.AUDITS)
        .containsSubsequence("configure-test-1", "purge-test-1", "close-test-1")
        .containsSubsequence("configure-test-2", "purge-test-2", "close-test-2")
        .doesNotContainAnyElementsOf(
            Arrays.asList("open-test-1", "export-test-1", "open-test-2", "export-test-2"));
  }

  @Test
  void shouldCallNodeIdProviderScale() {
    // given
    final var nodeIdProvider = mock(NodeIdProvider.class);
    when(nodeIdProvider.scale(anyInt())).thenReturn(CompletableFuture.completedFuture(null));
    final var executor =
        new ClusterChangeExecutorImpl(
            new TestConcurrencyControl(), new ExporterRepository(), nodeIdProvider, null);

    // when
    final var result =
        executor.preScaling(1, Set.of(MemberId.from("0"), MemberId.from("1"), MemberId.from("2")));

    // then
    Assertions.assertThat(result).succeedsWithin(Duration.ofSeconds(5));
    verify(nodeIdProvider, times(1)).scale(3);
  }

  @Test
  void shouldOnlyCallNodeIdProviderScaleWhenScalingUp() {
    // given
    final var nodeIdProvider = mock(NodeIdProvider.class);
    when(nodeIdProvider.scale(anyInt())).thenReturn(CompletableFuture.completedFuture(null));
    final var executor =
        new ClusterChangeExecutorImpl(
            new TestConcurrencyControl(), new ExporterRepository(), nodeIdProvider, null);

    // when
    final var result = executor.preScaling(3, Set.of(MemberId.from("0"), MemberId.from("1")));

    // then
    Assertions.assertThat(result).succeedsWithin(Duration.ofSeconds(5));
    verify(nodeIdProvider, times(0)).scale(anyInt());
  }

  @Test
  void shouldCompleteExceptionallyWhenNodeIdProviderScaleFails() {
    // given
    final var nodeIdProvider = mock(NodeIdProvider.class);
    when(nodeIdProvider.scale(anyInt()))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("scale failed")));
    final var executor =
        new ClusterChangeExecutorImpl(
            new TestConcurrencyControl(), new ExporterRepository(), nodeIdProvider, null);

    // when
    final var result =
        executor.preScaling(1, Set.of(MemberId.from("0"), MemberId.from("1"), MemberId.from("2")));

    // then
    Assertions.assertThat(result)
        .failsWithin(Duration.ofSeconds(5))
        .withThrowableOfType(ExecutionException.class)
        .withMessageContaining("scale failed");
    verify(nodeIdProvider, times(1)).scale(3);
  }

  public static class AuditExporter implements Exporter {
    static final List<String> AUDITS = new ArrayList<>();
    String exporterId;

    @Override
    public void configure(final Context context) throws Exception {
      exporterId = context.getConfiguration().getId();
      audit("configure");
    }

    @Override
    public void close() {
      audit("close");
    }

    @Override
    public void export(final Record<?> record) {
      audit("export");
    }

    @Override
    public void purge() throws Exception {
      audit("purge");
      Exporter.super.purge();
    }

    private void audit(final String name) {
      AUDITS.add(name + "-" + (exporterId != null ? exporterId : "unknown"));
    }
  }
}
