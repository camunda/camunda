/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.debug.cli.recover;

import static io.camunda.debug.cli.util.ErrorMessageUtil.rootMessage;

import io.camunda.exporter.handlers.EmbeddedFormHandler;
import io.camunda.exporter.handlers.ExportHandler;
import io.camunda.exporter.handlers.ProcessHandler;
import io.camunda.exporter.index.TargetIndex;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.zeebe.engine.state.deployment.PersistedProcess;
import io.camunda.zeebe.engine.state.deployment.PersistedProcess.PersistedProcessState;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.deployment.Process;
import java.io.PrintWriter;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Re-exports process definitions read from primary storage (RocksDB) into secondary storage (ES/OS)
 * by driving the real {@link ExportHandler}s off a {@link RecoveredProcessRecord}. Only {@code
 * ACTIVE} definitions are recovered; a definition already present in secondary storage is skipped
 * unless {@code override} is set. All writes are id-keyed overwrites, so recovery is idempotent and
 * safe to re-run.
 *
 * <p>This class is deliberately free of any RocksDB or ES/OS wiring so it can be unit-tested with a
 * fake {@link ExistingDocuments} predicate and a fake {@link BatchRequest}.
 */
final class ProcessDefinitionRecovery {

  private final ProcessHandler processHandler;
  private final EmbeddedFormHandler embeddedFormHandler;
  private final Supplier<BatchRequest> batchRequestFactory;
  private final ExistingDocuments existingDocuments;
  private final boolean override;
  private final boolean dryRun;
  private final int batchSize;
  private final PrintWriter progress;

  ProcessDefinitionRecovery(
      final ProcessHandler processHandler,
      final EmbeddedFormHandler embeddedFormHandler,
      final Supplier<BatchRequest> batchRequestFactory,
      final ExistingDocuments existingDocuments,
      final boolean override,
      final boolean dryRun,
      final int batchSize,
      final PrintWriter progress) {
    this.processHandler = processHandler;
    this.embeddedFormHandler = embeddedFormHandler;
    this.batchRequestFactory = batchRequestFactory;
    this.existingDocuments = existingDocuments;
    this.override = override;
    this.dryRun = dryRun;
    this.batchSize = batchSize;
    this.progress = progress;
  }

  /**
   * Iterates the given source of persisted processes and recovers each one. Never throws for a
   * single failed definition: failures are counted and reported so a single corrupt definition does
   * not abort the whole recovery.
   */
  Summary run(final ProcessSource source) {
    final var run = new Run();
    source.forEach(run::accept);
    run.flush();
    return run.summary();
  }

  /**
   * Builds every entity the handler derives from {@code record} and, unless {@code batch} is {@code
   * null} (dry-run), flushes it.
   */
  private <T extends ExporterEntity<T>> void drive(
      final ExportHandler<T, Process> handler,
      final Record<Process> record,
      final BatchRequest batch) {
    for (final String id : handler.generateIds(record)) {
      final T entity = handler.createNewEntity(id);
      handler.updateEntity(record, entity);
      if (batch != null) {
        final var index = TargetIndex.mainIndex(handler.getIndexName());
        handler.flush(index, entity, batch);
      }
    }
  }

  /** Outcome counts of a recovery run. */
  record Summary(long total, long alreadyPresent, long written, long skippedInactive, long failed) {

    boolean hasFailures() {
      return failed > 0;
    }
  }

  /** A source of persisted processes, e.g. {@code DbProcessState#forEachProcess}. */
  @FunctionalInterface
  interface ProcessSource {
    void forEach(Consumer<PersistedProcess> consumer);
  }

  /** Existence check for a process-definition document in secondary storage, keyed by its id. */
  @FunctionalInterface
  interface ExistingDocuments {
    boolean exists(long processDefinitionKey);
  }

  /** Holds the mutable per-run state (current batch + counters). */
  private final class Run {
    private BatchRequest batch;
    private int pendingInBatch;

    private long total;
    private long alreadyPresent;
    private long written;
    private long skippedInactive;
    private long failed;

    private void accept(final PersistedProcess process) {
      if (process.getState() != PersistedProcessState.ACTIVE) {
        skippedInactive++;
        return;
      }
      total++;

      final long key = process.getKey();
      try {
        final boolean exists = existingDocuments.exists(key);
        if (exists) {
          alreadyPresent++;
          if (!override) {
            return;
          }
        }

        final Record<Process> record = RecoveredProcessRecord.from(process);
        if (dryRun) {
          // Exercise the mapping to surface any conversion error, but write nothing.
          drive(processHandler, record, null);
          drive(embeddedFormHandler, record, null);
          written++;
        } else {
          drive(processHandler, record, currentBatch());
          drive(embeddedFormHandler, record, currentBatch());
          // Counted optimistically here; a failing batch flush moves these back to `failed`.
          written++;
          pendingInBatch++;
          if (pendingInBatch >= batchSize) {
            flush();
          }
        }
      } catch (final Exception e) {
        failed++;
        progress.println("Failed to recover process definition " + key + ": " + rootMessage(e));
      }
    }

    private BatchRequest currentBatch() {
      if (batch == null) {
        batch = batchRequestFactory.get();
      }
      return batch;
    }

    private void flush() {
      if (batch == null || pendingInBatch == 0) {
        return;
      }
      try {
        batch.execute();
      } catch (final Exception e) {
        // The whole batch failed to apply: move its definitions from written to failed.
        written -= pendingInBatch;
        failed += pendingInBatch;
        progress.println(
            "Failed to flush a batch of " + pendingInBatch + " definitions: " + rootMessage(e));
      } finally {
        batch = null;
        pendingInBatch = 0;
      }
    }

    private Summary summary() {
      return new Summary(total, alreadyPresent, written, skippedInactive, failed);
    }
  }
}
