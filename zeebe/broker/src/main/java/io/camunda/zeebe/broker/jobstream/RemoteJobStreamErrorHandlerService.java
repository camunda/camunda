/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.jobstream;

import io.camunda.zeebe.broker.PartitionListener;
import io.camunda.zeebe.engine.state.QueryService;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.protocol.impl.stream.job.ActivatedJob;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.transport.stream.api.RemoteStreamErrorHandler;
import java.util.Objects;

/**
 * An empty actor which is used as the execution context for the {@link
 * RemoteJobStreamErrorHandler}. We split this off from the handler itself to simplify testing and
 * not rely on a complete actor scheduler.
 */
public final class RemoteJobStreamErrorHandlerService extends Actor
    implements PartitionListener, RemoteStreamErrorHandler<ActivatedJob> {
  private final RemoteJobStreamErrorHandler delegate;
  private final String name;

  public RemoteJobStreamErrorHandlerService(final JobStreamErrorHandler errorHandler) {
    delegate = new RemoteJobStreamErrorHandler(errorHandler);
    name = "RemoteJobStreamErrorHandler";
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public ActorFuture<Void> onBecomingFollower(final int partitionId, final long term) {
    return actor.call(() -> delegate.removeWriter(partitionId));
  }

  @Override
  public ActorFuture<Void> onBecomingLeader(
      final int partitionId,
      final long term,
      final LogStream logStream,
      final QueryService queryService) {
    final var writer =
        Objects.requireNonNull(logStream, "must specify a log stream").newLogStreamWriter();
    delegate.addWriter(partitionId, writer);
    return CompletableActorFuture.completed(null);
  }

  @Override
  public ActorFuture<Void> onBecomingInactive(final int partitionId, final long term) {
    return actor.call(() -> delegate.removeWriter(partitionId));
  }

  @Override
  public void handleError(final Throwable error, final ActivatedJob job) {
    actor.run(() -> delegate.handleError(error, job));
  }
}
