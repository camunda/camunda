/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.partitioning;

import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.sched.future.CompletableActorFuture;
import org.slf4j.Logger;

public final class NoOpPartitionAdminAccess implements PartitionAdminAccess {

  private static final Logger LOG = Loggers.SYSTEM_LOGGER;

  @Override
  public ActorFuture<Void> takeSnapshot() {
    logCall();
    return CompletableActorFuture.completed(null);
  }

  @Override
  public ActorFuture<Void> pauseExporting() {
    logCall();
    return CompletableActorFuture.completed(null);
  }

  @Override
  public ActorFuture<Void> resumeExporting() {
    logCall();
    return CompletableActorFuture.completed(null);
  }

  @Override
  public ActorFuture<Void> pauseProcessing() {
    logCall();
    return CompletableActorFuture.completed(null);
  }

  @Override
  public ActorFuture<Void> resumeProcessing() {
    logCall();
    return CompletableActorFuture.completed(null);
  }

  private void logCall() {
    LOG.warn("Received call on NoOp implementation of PartitionAdminAccess");
  }
}
