/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor;

import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.state.StateSnapshotController;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import java.time.Duration;

public class AsyncSnapshotingDirectorService implements Service<AsyncSnapshotingDirectorService> {
  private final Injector<StreamProcessor> streamProcessorInjector = new Injector<>();

  private final LogStream logStream;
  private final StateSnapshotController snapshotController;
  private final Duration snapshotPeriod;
  private AsyncSnapshotDirector asyncSnapshotDirector;

  public AsyncSnapshotingDirectorService(
      final LogStream logStream,
      final StateSnapshotController snapshotController,
      Duration snapshotPeriod) {
    this.logStream = logStream;
    this.snapshotController = snapshotController;
    this.snapshotPeriod = snapshotPeriod;
  }

  @Override
  public void start(final ServiceStartContext startContext) {
    final StreamProcessor streamProcessor = streamProcessorInjector.getValue();

    asyncSnapshotDirector =
        new AsyncSnapshotDirector(streamProcessor, snapshotController, logStream, snapshotPeriod);

    startContext.getScheduler().submitActor(asyncSnapshotDirector);
  }

  @Override
  public void stop(final ServiceStopContext stopContext) {
    if (asyncSnapshotDirector != null) {
      stopContext.async(asyncSnapshotDirector.closeAsync());
      asyncSnapshotDirector = null;
    }
  }

  @Override
  public AsyncSnapshotingDirectorService get() {
    return this;
  }

  public Injector<StreamProcessor> getStreamProcessorInjector() {
    return streamProcessorInjector;
  }
}
