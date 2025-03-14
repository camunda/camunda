/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl;

import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.stream.api.scheduling.AsyncSchedulePool;
import io.camunda.zeebe.stream.api.scheduling.SimpleProcessingScheduleService;
import io.camunda.zeebe.stream.impl.AsyncUtil.Step;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

class AsyncScheduleServiceContext {
  private final ActorSchedulingService actorSchedulingService;
  private final ProcessingScheduleServiceFactory actorServiceFactory;
  private final int partitionId;

  private final Map<AsyncSchedulePool, AsyncProcessingScheduleServiceActor> asyncActors;
  private final Map<AsyncSchedulePool, ProcessingScheduleServiceImpl> asyncActorServices;

  public AsyncScheduleServiceContext(
      final ActorSchedulingService actorSchedulingService,
      final ProcessingScheduleServiceFactory actorServiceFactory,
      final int partitionId) {
    this.actorSchedulingService = actorSchedulingService;
    this.actorServiceFactory = actorServiceFactory;

    this.partitionId = partitionId;

    asyncActorServices = Collections.unmodifiableMap(createAsyncActorServices());
    asyncActors = Collections.unmodifiableMap(createAsyncActors(asyncActorServices));
  }

  public AsyncProcessingScheduleServiceActor geAsyncActor(final AsyncSchedulePool pool) {
    return asyncActors.get(pool);
  }

  public SimpleProcessingScheduleService getAsyncActorService(final AsyncSchedulePool pool) {
    return asyncActorServices.get(pool);
  }

  public ActorFuture<Void> closeActorsAsync() {
    final Step[] array =
        asyncActors.values().stream().map(a -> (Step) a::closeAsync).toArray(Step[]::new);
    return AsyncUtil.chainSteps(0, array);
  }

  public void closeActorServices() {
    asyncActorServices.values().forEach(ProcessingScheduleServiceImpl::close);
  }

  public ActorFuture<Void> submitActors() {
    final Step[] submitActorSteps =
        asyncActors.entrySet().stream()
            .map((e) -> (Step) () -> submitActor(e.getKey(), e.getValue()))
            .toArray(Step[]::new);
    return AsyncUtil.chainSteps(0, submitActorSteps);
  }

  private ActorFuture<Void> submitActor(
      final AsyncSchedulePool pool, final AsyncProcessingScheduleServiceActor actor) {
    return actorSchedulingService.submitActor(actor, pool.getSchedulingHints());
  }

  private Map<AsyncSchedulePool, ProcessingScheduleServiceImpl> createAsyncActorServices() {
    return Arrays.stream(AsyncSchedulePool.values())
        .collect(Collectors.toMap(Function.identity(), ignored -> actorServiceFactory.create()));
  }

  private Map<AsyncSchedulePool, AsyncProcessingScheduleServiceActor> createAsyncActors(
      final Map<AsyncSchedulePool, ProcessingScheduleServiceImpl> services) {
    return Arrays.stream(AsyncSchedulePool.values())
        .collect(
            Collectors.toMap(
                Function.identity(),
                pool ->
                    new AsyncProcessingScheduleServiceActor(
                        pool.getName(), services.get(pool), partitionId)));
  }
}
