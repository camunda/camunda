/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.engine;

import io.zeebe.broker.clustering.base.partitions.Partition;
import io.zeebe.engine.processor.AsyncSnapshotDirector;
import io.zeebe.engine.processor.StreamProcessor;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.util.sched.future.ActorFuture;
import java.time.Duration;

public class AsyncSnapshotingDirectorService implements Service<AsyncSnapshotingDirectorService> {
  private final Injector<StreamProcessor> streamProcessorInjector = new Injector<>();
  private final Injector<Partition> partitionInjector = new Injector<>();
  private final Duration snapshotPeriod;
  private AsyncSnapshotDirector asyncSnapshotDirector;

  public AsyncSnapshotingDirectorService(Duration snapshotPeriod) {
    this.snapshotPeriod = snapshotPeriod;
  }

  @Override
  public void start(final ServiceStartContext startContext) {
    final StreamProcessor streamProcessor = streamProcessorInjector.getValue();

    final Partition partition = partitionInjector.getValue();
    asyncSnapshotDirector =
        new AsyncSnapshotDirector(
            streamProcessor,
            partition.getSnapshotController(),
            partition.getLogStream(),
            snapshotPeriod);

    final ActorFuture<Void> startFuture =
        startContext.getScheduler().submitActor(asyncSnapshotDirector);
    startContext.async(startFuture);
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

  public Injector<Partition> getPartitionInjector() {
    return partitionInjector;
  }
}
