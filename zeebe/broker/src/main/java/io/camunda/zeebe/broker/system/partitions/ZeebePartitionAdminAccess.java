/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions;

import static java.util.Objects.requireNonNull;

import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.partitioning.PartitionAdminAccess;
import io.camunda.zeebe.broker.system.configuration.FlowControlCfg;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.state.migration.DbMigrationState;
import io.camunda.zeebe.engine.state.processing.DbBannedInstanceState;
import io.camunda.zeebe.logstreams.impl.flowcontrol.FlowControl;
import io.camunda.zeebe.logstreams.impl.flowcontrol.FlowControlLimits;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.logstreams.log.LogStreamWriter.WriteFailure;
import io.camunda.zeebe.logstreams.log.WriteContext;
import io.camunda.zeebe.protocol.impl.encoding.MigrationStatusCode;
import io.camunda.zeebe.protocol.impl.encoding.PartitionMigrationStatus;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.error.ErrorRecord;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ErrorIntent;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.stream.impl.records.RecordBatchEntry;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.VersionUtil;
import io.camunda.zeebe.util.migration.VersionCompatibilityCheck;
import io.camunda.zeebe.util.migration.VersionCompatibilityCheck.CheckResult.Compatible;
import io.camunda.zeebe.util.migration.VersionCompatibilityCheck.CheckResult.Incompatible;
import io.camunda.zeebe.util.migration.VersionCompatibilityCheck.CheckResult.Indeterminate;
import java.io.IOException;
import java.util.Optional;
import org.slf4j.Logger;

class ZeebePartitionAdminAccess implements PartitionAdminAccess {

  private static final Logger LOG = Loggers.SYSTEM_LOGGER;

  private final ConcurrencyControl concurrencyControl;
  private final int partitionId;
  private final PartitionAdminControl adminControl;

  // Lazily built the first time the migration status is read, and reused on every later read --
  // each read otherwise opened a fresh transaction context on the live ZeebeDb (see
  // #migrationState) that nothing ever closed, leaking one native transaction per admin request.
  // Rebuilt only if the underlying ZeebeDb instance itself changes (e.g. a follower installing a
  // new snapshot), never per call.
  private ZeebeDb migrationStatusDb;
  private DbMigrationState migrationStatusState;

  ZeebePartitionAdminAccess(
      final ConcurrencyControl concurrencyControl,
      final int partitionId,
      final PartitionAdminControl adminControl) {
    this.concurrencyControl = requireNonNull(concurrencyControl);
    this.partitionId = partitionId;
    this.adminControl = requireNonNull(adminControl);
  }

  @Override
  public Optional<PartitionAdminAccess> forPartition(final int partitionId) {
    if (this.partitionId == partitionId) {
      return Optional.of(this);
    } else {
      return Optional.empty();
    }
  }

  @Override
  public ActorFuture<Void> takeSnapshot() {
    final ActorFuture<Void> completed = concurrencyControl.createFuture();

    concurrencyControl.run(
        () -> {
          try {
            adminControl.triggerSnapshot();
            completed.complete(null);
          } catch (final Exception e) {
            completed.completeExceptionally(e);
          }
        });

    return completed;
  }

  @Override
  public ActorFuture<Void> pauseProcessing() {
    final ActorFuture<Void> completed = concurrencyControl.createFuture();
    concurrencyControl.run(
        () -> {
          try {
            adminControl.pauseProcessing();

            if (adminControl.getStreamProcessor() != null && !adminControl.shouldProcess()) {
              adminControl.getStreamProcessor().pauseProcessing().onComplete(completed);
            } else {
              completed.complete(null);
            }
          } catch (final IOException e) {
            LOG.error("Could not pause processing state", e);
            completed.completeExceptionally(e);
          }
        });
    return completed;
  }

  @Override
  public ActorFuture<Void> resumeProcessing() {
    final ActorFuture<Void> completed = concurrencyControl.createFuture();
    concurrencyControl.run(
        () -> {
          try {
            adminControl.resumeProcessing();
            if (adminControl.getStreamProcessor() != null && adminControl.shouldProcess()) {
              adminControl.getStreamProcessor().resumeProcessing().onComplete(completed);
            } else {
              completed.complete(null);
            }
          } catch (final IOException e) {
            LOG.error("Could not resume processing", e);
            completed.completeExceptionally(e);
          }
        });
    return completed;
  }

  @Override
  public ActorFuture<Void> banInstance(final long processInstanceKey) {
    final ActorFuture<Void> future = concurrencyControl.createFuture();
    concurrencyControl.run(
        () -> {
          try {
            final var logStreamWriter = adminControl.getLogStream().newLogStreamWriter();
            writeErrorEventAndBanInstance(processInstanceKey, logStreamWriter, future);
          } catch (final Exception e) {
            LOG.error(
                "Failure on writing error record to ban instance {} onto the LogStream.",
                processInstanceKey,
                e);
            future.completeExceptionally(e);
          }
        });
    return future;
  }

  @Override
  public ActorFuture<Void> configureFlowControl(final FlowControlCfg flowControlCfg) {
    final ActorFuture<Void> future = concurrencyControl.createFuture();
    concurrencyControl.run(
        () -> {
          try {
            final FlowControl flowControl = adminControl.getLogStream().getFlowControl();
            if (flowControlCfg.getWrite() != null) {
              flowControl.setWriteRateLimit(flowControlCfg.getWrite().buildLimit());
            }
            if (flowControlCfg.getRequest() != null) {
              flowControl.setRequestLimit(flowControlCfg.getRequest().buildLimit());
            }
            future.complete(null);
          } catch (final Exception e) {
            LOG.error(
                "Failure on configuring the append limit of flow control with config {}.",
                flowControlCfg,
                e);
            future.completeExceptionally(e);
          }
        });
    return future;
  }

  @Override
  public ActorFuture<FlowControlLimits> getFlowControlConfiguration() {
    final ActorFuture<FlowControlLimits> future = concurrencyControl.createFuture();

    concurrencyControl.run(
        () -> {
          final var flowControl = adminControl.getLogStream().getFlowControl();
          try {
            final FlowControlLimits limits =
                new FlowControlLimits(
                    flowControl.getRequestLimit(), flowControl.getWriteRateLimit());
            future.complete(limits);
          } catch (final Exception e) {
            LOG.error("Failure on getting the limit configuration of flow control.", e);
            future.completeExceptionally(e);
          }
        });
    return future;
  }

  @Override
  public ActorFuture<PartitionMigrationStatus> getMigrationStatus() {
    final ActorFuture<PartitionMigrationStatus> future = concurrencyControl.createFuture();

    concurrencyControl.run(
        () -> {
          try {
            future.complete(readMigrationStatus());
          } catch (final Exception e) {
            LOG.error("Failed to determine the migration status of partition {}", partitionId, e);
            future.complete(
                new PartitionMigrationStatus(
                    MigrationStatusCode.UNKNOWN,
                    "partition "
                        + partitionId
                        + ": failed to read migration status: "
                        + e.getMessage()));
          }
        });

    return future;
  }

  @Override
  public ActorFuture<PartitionMigrationStatus> getExportingMigrationStatus() {
    final ActorFuture<PartitionMigrationStatus> future = concurrencyControl.createFuture();

    concurrencyControl.run(
        () -> {
          try {
            final var exporterDirector = adminControl.getExporterDirector();
            if (exporterDirector == null) {
              future.complete(
                  new PartitionMigrationStatus(
                      MigrationStatusCode.UNKNOWN,
                      "partition "
                          + partitionId
                          + ": no exporter director running on this replica"
                          + " yet"));
              return;
            }
            exporterDirector.getExportingMigrationStatus().onComplete(future);
          } catch (final Exception e) {
            LOG.error(
                "Failed to determine the exporting migration status of partition {}",
                partitionId,
                e);
            future.complete(
                new PartitionMigrationStatus(
                    MigrationStatusCode.UNKNOWN,
                    "partition "
                        + partitionId
                        + ": failed to read exporting migration status: "
                        + e.getMessage()));
          }
        });

    return future;
  }

  /**
   * Reads {@code DbMigrationState.getMigratedByVersion()} through a second transaction on the
   * already-open, live {@code ZeebeDb}, without disturbing the stream processor's own state — the
   * same technique {@link #banInstanceInState} already uses for {@code DbBannedInstanceState}.
   */
  private PartitionMigrationStatus readMigrationStatus() {
    final var zeebeDb = adminControl.getZeebeDb();
    if (zeebeDb == null) {
      return new PartitionMigrationStatus(
          MigrationStatusCode.UNKNOWN,
          "partition " + partitionId + ": no ZeebeDb open on this replica yet");
    }

    final var migrationState = migrationState(zeebeDb);
    final var migratedByVersion = migrationState.getMigratedByVersion();
    if (migratedByVersion == null) {
      return new PartitionMigrationStatus(
          MigrationStatusCode.MIGRATION_IN_PROGRESS,
          "partition " + partitionId + ": no migrated-by-version recorded yet");
    }

    // Same comparison DbMigratorImpl itself uses to gate migrations — kept consistent so this
    // read-only status check never disagrees with the engine's own compatibility decision.
    final var result = VersionCompatibilityCheck.check(migratedByVersion, VersionUtil.getVersion());
    return switch (result) {
      case Compatible.SameVersion same -> migratedAndSnapshotted(same.version().toString());
      case Compatible.PatchUpgrade patch ->
          notYetMigrated(patch.from().toString(), patch.to().toString());
      case Compatible.MinorUpgrade minor ->
          notYetMigrated(minor.from().toString(), minor.to().toString());
      case Incompatible incompatible ->
          new PartitionMigrationStatus(
              MigrationStatusCode.UNKNOWN,
              "partition " + partitionId + ": incompatible migration path: " + incompatible);
      case Indeterminate indeterminate ->
          new PartitionMigrationStatus(
              MigrationStatusCode.UNKNOWN,
              "partition "
                  + partitionId
                  + ": cannot determine migration compatibility: "
                  + indeterminate);
    };
  }

  /**
   * Reuses one transaction context for the life of a given {@code ZeebeDb} instance instead of
   * opening a fresh one on every status read — this method may run once per admin request, and
   * {@code TransactionContext} has no way to close and release the native transaction it wraps, so
   * a new one per call would leak for as long as the {@code ZeebeDb} stays open. Rebuilt only if
   * the db instance itself changes, e.g. a follower installing a new snapshot.
   */
  private DbMigrationState migrationState(final ZeebeDb zeebeDb) {
    if (migrationStatusState == null || migrationStatusDb != zeebeDb) {
      migrationStatusDb = zeebeDb;
      migrationStatusState = new DbMigrationState(zeebeDb, zeebeDb.createContext());
    }
    return migrationStatusState;
  }

  private PartitionMigrationStatus migratedAndSnapshotted(final String version) {
    if (adminControl.isMigrationSnapshotTaken()) {
      return new PartitionMigrationStatus(
          MigrationStatusCode.MIGRATED,
          "partition " + partitionId + ": migrated to " + version + " and snapshotted");
    }
    return new PartitionMigrationStatus(
        MigrationStatusCode.MIGRATION_IN_PROGRESS,
        "partition " + partitionId + ": migrated to " + version + " but not yet snapshotted");
  }

  private PartitionMigrationStatus notYetMigrated(final String from, final String to) {
    return new PartitionMigrationStatus(
        MigrationStatusCode.MIGRATION_IN_PROGRESS,
        "partition "
            + partitionId
            + ": migrated-by-version "
            + from
            + " has not yet migrated to "
            + to);
  }

  private void writeErrorEventAndBanInstance(
      final long processInstanceKey, final LogStreamWriter writer, final ActorFuture<Void> future) {
    tryWriteErrorEvent(writer, processInstanceKey)
        .ifRightOrLeft(
            position -> {
              LOG.info("Wrote error record on position {}", position);
              // we only want to make the state change after we wrote the event
              banInstanceInState(processInstanceKey);
              LOG.info("Successfully banned instance with key {}", processInstanceKey);
              future.complete(null);
            },
            writeFailure -> {
              final String errorMsg =
                  String.format(
                      "Failure %s on writing error record to ban instance %d",
                      writeFailure, processInstanceKey);
              future.completeExceptionally(new IllegalStateException(errorMsg));
              LOG.error(errorMsg);
            });
  }

  private void banInstanceInState(final long processInstanceKey) {
    final var zeebeDb = adminControl.getZeebeDb();
    final var context = zeebeDb.createContext();
    final var dbBannedInstanceState = new DbBannedInstanceState(zeebeDb, context);

    dbBannedInstanceState.banProcessInstance(processInstanceKey);
  }

  private static Either<WriteFailure, Long> tryWriteErrorEvent(
      final LogStreamWriter writer, final long processInstanceKey) {
    final var errorRecord = new ErrorRecord();
    errorRecord.initErrorRecord(new Exception("Instance was banned from outside."), -1);
    errorRecord.setProcessInstanceKey(processInstanceKey);

    final var recordMetadata =
        new RecordMetadata()
            .recordType(RecordType.EVENT)
            .valueType(ValueType.ERROR)
            .intent(ErrorIntent.CREATED)
            .recordVersion(RecordMetadata.DEFAULT_RECORD_VERSION)
            .rejectionType(RejectionType.NULL_VAL)
            .rejectionReason("");
    final var entry =
        RecordBatchEntry.createEntry(processInstanceKey, recordMetadata, -1, errorRecord);
    return writer.tryWrite(WriteContext.internal(), entry);
  }
}
