/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl;

import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.AsyncClosable;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.stream.api.scheduling.AsyncTaskGroup;
import io.camunda.zeebe.stream.impl.AsyncUtil.Step;
import java.util.EnumMap;

class AsyncScheduleServiceContext implements AsyncClosable {
  private final ActorSchedulingService actorSchedulingService;
  private final ProcessingScheduleServiceFactory actorServiceFactory;
  private final int partitionId;

  private final EnumMap<AsyncTaskGroup, AsyncProcessingScheduleServiceActor> asyncActors;

  public AsyncScheduleServiceContext(
      final ActorSchedulingService actorSchedulingService,
      final ProcessingScheduleServiceFactory actorServiceFactory,
      final int partitionId) {
    this.actorSchedulingService = actorSchedulingService;
    this.actorServiceFactory = actorServiceFactory;

    this.partitionId = partitionId;

    // create the async actors for all defined task groups
    asyncActors = createAsyncActors();
  }

  public AsyncProcessingScheduleServiceActor geAsyncActor(final AsyncTaskGroup taskGroup) {
    return asyncActors.get(taskGroup);
  }

  public ActorFuture<Void> submitActors() {
    final Step[] submitActorSteps =
        asyncActors.entrySet().stream()
            .map((e) -> (Step) () -> submitActor(e.getKey(), e.getValue()))
            .toArray(Step[]::new);
    return AsyncUtil.chainSteps(0, submitActorSteps);
  }

  @Override
  public ActorFuture<Void> closeAsync() {
    final Step[] array =
        asyncActors.values().stream().map(a -> (Step) a::closeAsync).toArray(Step[]::new);
    return AsyncUtil.chainSteps(0, array);
  }

  private ActorFuture<Void> submitActor(
      final AsyncTaskGroup taskGroup, final AsyncProcessingScheduleServiceActor actor) {
    return actorSchedulingService.submitActor(actor, taskGroup.getSchedulingHints());
  }

  private EnumMap<AsyncTaskGroup, AsyncProcessingScheduleServiceActor> createAsyncActors() {
    final var groups =
        new EnumMap<AsyncTaskGroup, AsyncProcessingScheduleServiceActor>(AsyncTaskGroup.class);
    for (final var group : AsyncTaskGroup.values()) {
      groups.put(
          group,
          new AsyncProcessingScheduleServiceActor(
              group.getName(), actorServiceFactory, partitionId));
    }
    return groups;
  }
}
