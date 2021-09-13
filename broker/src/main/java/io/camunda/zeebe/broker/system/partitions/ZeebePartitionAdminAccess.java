/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions;

import static java.util.Objects.requireNonNull;

import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.partitioning.PartitionAdminAccess;
import io.camunda.zeebe.util.sched.ConcurrencyControl;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import java.io.IOException;
import org.slf4j.Logger;

class ZeebePartitionAdminAccess implements PartitionAdminAccess {

  private static final Logger LOG = Loggers.SYSTEM_LOGGER;

  private final ConcurrencyControl concurrencyControl;
  private final PartitionAdminControl adminControl;

  ZeebePartitionAdminAccess(
      final ConcurrencyControl concurrencyControl, final PartitionAdminControl adminControl) {
    this.concurrencyControl = requireNonNull(concurrencyControl);
    this.adminControl = requireNonNull(adminControl);
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
  public ActorFuture<Void> pauseExporting() {
    final ActorFuture<Void> completed = concurrencyControl.createFuture();
    concurrencyControl.run(
        () -> {
          try {
            final var pauseStatePersisted = adminControl.pauseExporting();

            if (adminControl.getExporterDirector() != null && pauseStatePersisted) {
              adminControl.getExporterDirector().pauseExporting().onComplete(completed);
            } else {
              completed.complete(null);
            }
          } catch (final IOException e) {
            LOG.error("Could not pause exporting", e);
            completed.completeExceptionally(e);
          }
        });
    return completed;
  }

  @Override
  public ActorFuture<Void> resumeExporting() {
    final ActorFuture<Void> completed = concurrencyControl.createFuture();
    concurrencyControl.run(
        () -> {
          try {
            adminControl.resumeExporting();
            if (adminControl.getExporterDirector() != null && adminControl.shouldExport()) {
              adminControl.getExporterDirector().resumeExporting().onComplete(completed);
            } else {
              completed.complete(null);
            }
          } catch (final IOException e) {
            LOG.error("Could not resume exporting", e);
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
              adminControl.getStreamProcessor().resumeProcessing();
            }
            completed.complete(null);
          } catch (final IOException e) {
            LOG.error("Could not resume processing", e);
            completed.completeExceptionally(e);
          }
        });
    return completed;
  }
}
