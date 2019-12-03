/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.logstreams.delete;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.exporter.stream.ExporterDirector;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.state.Snapshot;
import io.zeebe.logstreams.state.SnapshotDeletionListener;
import io.zeebe.util.sched.future.ActorFuture;

public class LeaderLogStreamDeletionService implements SnapshotDeletionListener {
  private final LogStream logStream;
  private final ExporterDirector exporterDirector;

  public LeaderLogStreamDeletionService(
      final LogStream logStream, final ExporterDirector exporterDirector) {
    this.logStream = logStream;
    this.exporterDirector = exporterDirector;
  }

  @Override
  public void onSnapshotDeleted(final Snapshot snapshot) {
    final ActorFuture<Long> lowestExporterPosition = exporterDirector.getLowestExporterPosition();
    lowestExporterPosition.onComplete(
        (value, exception) -> {
          if (exception == null) {
            final long minPosition = Math.min(snapshot.getPosition(), value);
            logStream.delete(minPosition);
          } else {
            Loggers.DELETION_SERVICE.warn(
                "Expected to retrieve lowest exporter position, but exception occurred.",
                exception);
          }
        });
  }
}
