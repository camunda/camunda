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
import io.zeebe.logstreams.impl.delete.DeletionService;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.util.sched.future.ActorFuture;

public class LeaderLogStreamDeletionService implements DeletionService, Service {
  private final Injector<ExporterDirectorService> exporterDirectorInjector = new Injector<>();
  private final LogStream logStream;
  private ExporterDirectorService exporterDirector;

  public LeaderLogStreamDeletionService(LogStream logStream) {
    this.logStream = logStream;
  }

  @Override
  public void start(final ServiceStartContext startContext) {
    exporterDirector = exporterDirectorInjector.getValue();
  }

  @Override
  public void stop(final ServiceStopContext stopContext) {}

  @Override
  public LeaderLogStreamDeletionService get() {
    return this;
  }

  @Override
  public void delete(final long position) {
    final ActorFuture<Long> lowestExporterPosition = exporterDirector.getLowestExporterPosition();
    lowestExporterPosition.onComplete(
        (value, exception) -> {
          if (exception == null) {
            final long minPosition = Math.min(position, value);
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
