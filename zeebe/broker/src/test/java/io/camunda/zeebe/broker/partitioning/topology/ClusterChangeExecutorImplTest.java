/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.topology;

import io.camunda.zeebe.broker.exporter.repo.ExporterLoadException;
import io.camunda.zeebe.broker.exporter.repo.ExporterRepository;
import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
            new TestConcurrencyControl(), repository, new SimpleMeterRegistry());

    // when
    final Future<Void> result = executor.deleteHistory();

    // then
    Assertions.assertThat(result).succeedsWithin(Duration.ofSeconds(5));
    Assertions.assertThat(AuditExporter.AUDITS)
        .containsSubsequence("configure-test-1", "purge-test-1", "close-test-1");
    Assertions.assertThat(AuditExporter.AUDITS)
        .containsSubsequence("configure-test-2", "purge-test-2", "close-test-2");
    Assertions.assertThat(AuditExporter.AUDITS)
        .doesNotContainAnyElementsOf(
            Arrays.asList("open-test-1", "export-test-1", "open-test-2", "export-test-2"));
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
