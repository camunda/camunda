/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.debug.cli.recover;

import static io.camunda.debug.cli.recover.RecoverTestSupport.persistedProcess;
import static io.camunda.debug.cli.recover.RecoverTestSupport.readResource;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.debug.cli.recover.ProcessDefinitionRecovery.ExistingDocuments;
import io.camunda.debug.cli.recover.ProcessDefinitionRecovery.ProcessSource;
import io.camunda.debug.cli.recover.ProcessDefinitionRecovery.Summary;
import io.camunda.exporter.handlers.EmbeddedFormHandler;
import io.camunda.exporter.handlers.ProcessHandler;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.ProcessEntity;
import io.camunda.webapps.schema.entities.form.FormEntity;
import io.camunda.zeebe.engine.state.deployment.PersistedProcess;
import io.camunda.zeebe.engine.state.deployment.PersistedProcess.PersistedProcessState;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

final class ProcessDefinitionRecoveryTest {

  private static final String PROCESS_INDEX = "process";
  private static final String FORM_INDEX = "form";

  private final ProcessHandler processHandler =
      new ProcessHandler(PROCESS_INDEX, new NoopProcessCache());
  private final EmbeddedFormHandler embeddedFormHandler = new EmbeddedFormHandler(FORM_INDEX);

  @Test
  void shouldWriteOnlyMissingByDefault() throws IOException {
    // given — key 1 already present, key 2 missing; key 3 is not ACTIVE.
    final var batch = new FakeBatchRequest();
    final var recovery = recovery(batch, existing(1L), false, false, 50);

    // when
    final Summary summary =
        recovery.run(source(activeSimple(1L), activeSimple(2L), pendingDeletion(3L)));

    // then
    assertThat(summary.total()).isEqualTo(2);
    assertThat(summary.alreadyPresent()).isEqualTo(1);
    assertThat(summary.written()).isEqualTo(1);
    assertThat(summary.skippedInactive()).isEqualTo(1);
    assertThat(summary.failed()).isZero();
    assertThat(processIds(batch)).containsExactly("2");
  }

  @Test
  void shouldRewriteExistingWhenOverride() throws IOException {
    // given — both present, override on.
    final var batch = new FakeBatchRequest();
    final var recovery = recovery(batch, existing(1L, 2L), true, false, 50);

    // when
    final Summary summary = recovery.run(source(activeSimple(1L), activeSimple(2L)));

    // then
    assertThat(summary.total()).isEqualTo(2);
    assertThat(summary.alreadyPresent()).isEqualTo(2);
    assertThat(summary.written()).isEqualTo(2);
    assertThat(processIds(batch)).containsExactlyInAnyOrder("1", "2");
  }

  @Test
  void shouldWriteNothingOnDryRun() throws IOException {
    // given
    final var batch = new FakeBatchRequest();
    final var recovery = recovery(batch, existing(), false, true, 50);

    // when
    final Summary summary = recovery.run(source(activeSimple(1L), activeSimple(2L)));

    // then — diff is computed but nothing is written or executed.
    assertThat(summary.written()).isEqualTo(2);
    assertThat(summary.alreadyPresent()).isZero();
    assertThat(batch.added).isEmpty();
    assertThat(batch.executeCount).isZero();
  }

  @Test
  void shouldRecoverEmbeddedStartForm() throws IOException {
    // given — a process carrying an embedded start form.
    final var batch = new FakeBatchRequest();
    final var recovery = recovery(batch, existing(), false, false, 50);

    // when
    final Summary summary = recovery.run(source(activeEmbeddedForm(7L)));

    // then — both the process doc and its embedded form doc are written.
    assertThat(summary.written()).isEqualTo(1);
    assertThat(processIds(batch)).containsExactly("7");
    final List<FakeBatchRequest.Added> forms = batch.addedTo(FORM_INDEX);
    assertThat(forms).hasSize(1);
    final FormEntity form = (FormEntity) forms.get(0).entity();
    assertThat(form.getId()).isEqualTo("7_my-embedded-form");
    assertThat(form.getProcessDefinitionId()).isEqualTo("7");
  }

  @Test
  void shouldCountFailuresWhenBatchFlushFails() throws IOException {
    // given — the bulk request fails to apply; batch size 1 so each definition flushes on its own.
    final var batch = new FakeBatchRequest(true);
    final var recovery = recovery(batch, existing(), false, false, 1);

    // when
    final Summary summary = recovery.run(source(activeSimple(1L), activeSimple(2L)));

    // then — both definitions are reported as failed, none as written.
    assertThat(summary.written()).isZero();
    assertThat(summary.failed()).isEqualTo(2);
    assertThat(summary.hasFailures()).isTrue();
  }

  // --- helpers -------------------------------------------------------------

  private ProcessDefinitionRecovery recovery(
      final BatchRequest batch,
      final ExistingDocuments existing,
      final boolean override,
      final boolean dryRun,
      final int batchSize) {
    final Supplier<BatchRequest> factory = () -> batch;
    return new ProcessDefinitionRecovery(
        processHandler,
        embeddedFormHandler,
        factory,
        existing,
        override,
        dryRun,
        batchSize,
        new PrintWriter(new StringWriter()));
  }

  private static ExistingDocuments existing(final Long... keys) {
    final Set<Long> set = Set.of(keys);
    return set::contains;
  }

  private static ProcessSource source(final PersistedProcess... processes) {
    return consumer -> {
      for (final PersistedProcess process : processes) {
        consumer.accept(process);
      }
    };
  }

  private static List<String> processIds(final FakeBatchRequest batch) {
    return batch.addedTo(PROCESS_INDEX).stream()
        .map(a -> ((ProcessEntity) a.entity()).getId())
        .toList();
  }

  private static PersistedProcess activeSimple(final long key) throws IOException {
    return persistedProcess(
        key,
        "testProcessId",
        1,
        "",
        "<default>",
        "simple.bpmn",
        readResource("recover/simple-process.bpmn"),
        PersistedProcessState.ACTIVE);
  }

  private static PersistedProcess activeEmbeddedForm(final long key) throws IOException {
    return persistedProcess(
        key,
        "testProcessId",
        1,
        "processTag",
        "<default>",
        "embedded-form.bpmn",
        readResource("recover/embedded-form-process.bpmn"),
        PersistedProcessState.ACTIVE);
  }

  private static PersistedProcess pendingDeletion(final long key) throws IOException {
    return persistedProcess(
        key,
        "testProcessId",
        1,
        "",
        "<default>",
        "simple.bpmn",
        readResource("recover/simple-process.bpmn"),
        PersistedProcessState.PENDING_DELETION);
  }
}
