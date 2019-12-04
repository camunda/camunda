/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.logstreams;

import io.zeebe.broker.logstreams.state.StatePositionSupplier;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.state.Snapshot;
import io.zeebe.logstreams.state.SnapshotDeletionListener;
import io.zeebe.logstreams.state.SnapshotStorage;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.util.sched.Actor;
import java.util.Objects;

public class LogStreamDeletionService extends Actor
    implements Service<SnapshotDeletionListener>, SnapshotDeletionListener {
  private final Injector<SnapshotStorage> snapshotStorageInjector = new Injector<>();
  private final LogStream logStream;
  private final StatePositionSupplier positionSupplier;

  private SnapshotStorage snapshotStorage;

  public LogStreamDeletionService(
      final LogStream logStream, final StatePositionSupplier positionSupplier) {
    this.logStream = logStream;
    this.positionSupplier = positionSupplier;
  }

  @Override
  public void start(final ServiceStartContext startContext) {
    snapshotStorage =
        Objects.requireNonNull(
            getSnapshotStorageInjector().getValue(), "must have a snapshot storage");
    startContext.async(startContext.getScheduler().submitActor(this));
  }

  @Override
  public void stop(final ServiceStopContext stopContext) {
    stopContext.async(actor.close());
  }

  @Override
  public SnapshotDeletionListener get() {
    return this;
  }

  @Override
  protected void onActorStarting() {
    snapshotStorage.addDeletionListener(get());
  }

  @Override
  protected void onActorClosing() {
    if (snapshotStorage != null) {
      snapshotStorage.removeDeletionListener(get());
    }
  }

  @Override
  public void onSnapshotsDeleted(final Snapshot oldestRemainingSnapshot) {
    actor.run(() -> delegateDeletion(oldestRemainingSnapshot));
  }

  public Injector<SnapshotStorage> getSnapshotStorageInjector() {
    return snapshotStorageInjector;
  }

  private void delegateDeletion(final Snapshot snapshot) {
    final long minPosition = positionSupplier.getLowestPosition(snapshot.getPath());
    logStream.delete(minPosition);
  }
}
