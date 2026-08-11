/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.broker.exporter.repo.ExporterLoadException;
import io.camunda.zeebe.broker.exporter.repo.ExporterRepository;
import io.camunda.zeebe.dynamic.config.changes.ExporterPurgeException;
import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.protocol.record.Record;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class ExporterHistoryPurgerTest {

  private static final String PHYSICAL_TENANT_ID = "tenant-a";

  private final ExporterRepository repository = new ExporterRepository();

  @BeforeEach
  void resetAudits() {
    clearAudits();
  }

  @Test
  void shouldRunPurgeForEveryExporter() throws ExporterLoadException {
    // given
    register("test-1", AuditExporter.class);
    register("test-2", AuditExporter.class);
    final var purger = purger();

    // when
    purger.purgeAll();

    // then
    assertThat(AuditExporter.AUDITS)
        .containsSubsequence("configure-test-1", "purge-test-1", "close-test-1")
        .containsSubsequence("configure-test-2", "purge-test-2", "close-test-2")
        .doesNotContainAnyElementsOf(
            Arrays.asList("open-test-1", "export-test-1", "open-test-2", "export-test-2"));
  }

  @Test
  void shouldPurgeInTheContextOfItsOwnPhysicalTenant() throws ExporterLoadException {
    // given
    register("test-1", AuditExporter.class);
    final var purger = purger();

    // when
    purger.purgeAll();

    // then
    assertThat(AuditExporter.PHYSICAL_TENANT_IDS).containsExactly(PHYSICAL_TENANT_ID);
  }

  @Test
  void shouldWrapPurgeFailure() throws ExporterLoadException {
    // given
    register("failing", FailingExporter.class);
    final var purger = purger();

    // when
    assertThatThrownBy(purger::purgeAll)
        // then
        .isInstanceOf(ExporterPurgeException.class)
        .hasMessageContaining("failing")
        .hasRootCauseMessage("purge failed");
    assertThat(FailingExporter.purgeCalls).isOne();
  }

  @Test
  void shouldReleaseExporterWhenPurgeFails() throws ExporterLoadException {
    // given
    register("failing", FailingExporter.class);
    final var purger = purger();

    // when
    assertThatThrownBy(purger::purgeAll).isInstanceOf(ExporterPurgeException.class);

    // then — the purge is retried until it succeeds, so anything the exporter opened before
    // failing has to be released rather than leaked once per attempt
    assertThat(FailingExporter.closeCalls).isOne();
  }

  private ExporterHistoryPurger purger() {
    return new ExporterHistoryPurger(PHYSICAL_TENANT_ID, repository, new SimpleMeterRegistry());
  }

  /**
   * Registers an exporter and discards the audit trail of the validation that {@link
   * ExporterRepository} performs on registration — it configures and closes one instance — so that
   * the assertions only see what purging did.
   */
  private void register(final String id, final Class<? extends Exporter> exporterClass)
      throws ExporterLoadException {
    repository.validateAndAddExporterDescriptor(id, exporterClass, null);
    clearAudits();
  }

  private static void clearAudits() {
    AuditExporter.AUDITS.clear();
    AuditExporter.PHYSICAL_TENANT_IDS.clear();
    FailingExporter.purgeCalls = 0;
    FailingExporter.closeCalls = 0;
  }

  public static class AuditExporter implements Exporter {
    static final List<String> AUDITS = new ArrayList<>();
    static final List<String> PHYSICAL_TENANT_IDS = new ArrayList<>();
    String exporterId;

    @Override
    public void configure(final Context context) {
      exporterId = context.getConfiguration().getId();
      PHYSICAL_TENANT_IDS.add(context.getPhysicalTenantId());
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

  public static class FailingExporter implements Exporter {
    static int purgeCalls;
    static int closeCalls;

    @Override
    public void close() {
      closeCalls++;
    }

    @Override
    public void export(final Record<?> record) {}

    @Override
    public void purge() {
      purgeCalls++;
      throw new RuntimeException("purge failed");
    }
  }
}
