/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl;

import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.stream.api.scheduling.SimpleProcessingScheduleService;

final class AsyncProcessingScheduleServiceActor extends Actor {

  private final ProcessingScheduleServiceImpl scheduleService;
  private CompletableActorFuture<Void> closeFuture = CompletableActorFuture.completed(null);

  public AsyncProcessingScheduleServiceActor(
      final String name,
      final ProcessingScheduleServiceFactory scheduleServiceFactory,
      final PartitionId partitionId) {
    super(name, partitionId);
    scheduleService = scheduleServiceFactory.create();
  }

  @Override
  protected void onActorStarting() {
    final ActorFuture<Void> actorFuture = scheduleService.open(actor);
    actor.runOnCompletionBlockingCurrentPhase(
        actorFuture,
        (v, t) -> {
          if (t != null) {
            actor.fail(t);
          }
        });
    closeFuture = new CompletableActorFuture<>();
  }

  @Override
  protected void onActorClosing() {
    scheduleService.close();
  }

  @Override
  protected void onActorClosed() {
    closeFuture.complete(null);
  }

  @Override
  public CompletableActorFuture<Void> closeAsync() {
    actor.close();
    return closeFuture;
  }

  @Override
  public void onActorFailed() {
    scheduleService.close();
    closeFuture.complete(null);
  }

  public SimpleProcessingScheduleService getScheduleService() {
    return scheduleService;
  }
}
