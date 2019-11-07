/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.logstreams.delete;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.exporter.ExporterDirectorService;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.state.Snapshot;
import io.zeebe.logstreams.state.SnapshotDeletionListener;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.util.sched.future.ActorFuture;

public class LeaderLogStreamDeletionService implements SnapshotDeletionListener, Service<Void> {
  private final Injector<ExporterDirectorService> exporterDirectorInjector = new Injector<>();
  private final LogStream logStream;
  private ExporterDirectorService exporterDirector;

  public LeaderLogStreamDeletionService(final LogStream logStream) {
    this.logStream = logStream;
  }

  @Override
  public void start(final ServiceStartContext startContext) {
    exporterDirector = exporterDirectorInjector.getValue();
  }

  @Override
  public Void get() {
    return null;
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

  public Injector<ExporterDirectorService> getExporterDirectorInjector() {
    return exporterDirectorInjector;
  }
}
