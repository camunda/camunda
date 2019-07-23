/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.logstreams.delete;

import io.zeebe.broker.exporter.ExporterManagerService;
import io.zeebe.logstreams.impl.delete.DeletionService;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;

public class LeaderLogStreamDeletionService implements DeletionService, Service {
  private final Injector<ExporterManagerService> exporterManagerInjector = new Injector<>();
  private final LogStream logStream;
  private ExporterManagerService exporterManagerService;

  public LeaderLogStreamDeletionService(LogStream logStream) {
    this.logStream = logStream;
  }

  @Override
  public void start(final ServiceStartContext startContext) {
    exporterManagerService = exporterManagerInjector.getValue();
  }

  @Override
  public void stop(final ServiceStopContext stopContext) {}

  @Override
  public LeaderLogStreamDeletionService get() {
    return this;
  }

  @Override
  public void delete(final long position) {
    final long minPosition = Math.min(position, getMinimumExportedPosition());
    logStream.delete(minPosition);
  }

  private long getMinimumExportedPosition() {
    return exporterManagerService.getLowestExporterPosition();
  }

  public Injector<ExporterManagerService> getExporterManagerInjector() {
    return exporterManagerInjector;
  }
}
