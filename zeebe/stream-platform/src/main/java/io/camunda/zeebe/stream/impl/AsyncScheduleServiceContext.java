/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl;

import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.stream.api.scheduling.AsyncSchedulePool;
import io.camunda.zeebe.stream.api.scheduling.ScheduledCommandCache.StageableScheduledCommandCache;
import io.camunda.zeebe.stream.api.scheduling.SimpleProcessingScheduleService;
import io.camunda.zeebe.stream.impl.AsyncUtil.Step;
import io.camunda.zeebe.stream.impl.StreamProcessor.Phase;
import io.camunda.zeebe.stream.impl.metrics.ScheduledTaskMetrics;
import io.camunda.zeebe.util.collection.Tuple;
import java.time.Duration;
import java.time.InstantSource;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public class AsyncScheduleServiceContext {
  private final ActorSchedulingService actorSchedulingService;
  private final Supplier<Phase> streamProcessorPhaseSupplier;
  private final BooleanSupplier abortCondition;
  private final Supplier<LogStreamWriter> writerSupplier;
  private final StageableScheduledCommandCache commandCache;
  private final InstantSource clock;
  private final Duration interval;
  private final ScheduledTaskMetrics metrics;
  private final int partitionId;

  private final Map<AsyncSchedulePool, AsyncProcessingScheduleServiceActor> asyncActors;
  private final Map<AsyncSchedulePool, ProcessingScheduleServiceImpl> asyncActorServices;

  public AsyncScheduleServiceContext(
      final ActorSchedulingService actorSchedulingService,
      final Supplier<Phase> streamProcessorPhaseSupplier,
      final BooleanSupplier abortCondition,
      final Supplier<LogStreamWriter> writerSupplier,
      final StageableScheduledCommandCache commandCache,
      final InstantSource clock,
      final Duration interval,
      final ScheduledTaskMetrics metrics,
      final int partitionId) {
    this.actorSchedulingService = actorSchedulingService;
    this.streamProcessorPhaseSupplier = streamProcessorPhaseSupplier;
    this.abortCondition = abortCondition;
    this.writerSupplier = writerSupplier;
    this.commandCache = commandCache;
    this.clock = clock;
    this.interval = interval;
    this.metrics = metrics;
    this.partitionId = partitionId;

    final var allAsyncPools = createAllAsyncPools();
    asyncActors = Collections.unmodifiableMap(allAsyncPools.getLeft());
    asyncActorServices = Collections.unmodifiableMap(allAsyncPools.getRight());
  }

  public ProcessingScheduleServiceImpl createActorService() {
    return new ProcessingScheduleServiceImpl(
        streamProcessorPhaseSupplier, // this is volatile
        abortCondition,
        writerSupplier,
        commandCache,
        clock,
        interval,
        metrics);
  }

  public AsyncProcessingScheduleServiceActor geAsyncActor(final AsyncSchedulePool pool) {
    return asyncActors.get(pool);
  }

  public SimpleProcessingScheduleService getAsyncActorService(final AsyncSchedulePool pool) {
    return asyncActorServices.get(pool);
  }

  public Tuple<AsyncProcessingScheduleServiceActor, ProcessingScheduleServiceImpl> createAsyncActor(
      final AsyncSchedulePool pool) {
    final var actorService = createActorService();
    final var actor =
        new AsyncProcessingScheduleServiceActor(pool.getName(), actorService, partitionId);

    return new Tuple<>(actor, actorService);
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

  private Tuple<
          Map<AsyncSchedulePool, AsyncProcessingScheduleServiceActor>,
          Map<AsyncSchedulePool, ProcessingScheduleServiceImpl>>
      createAllAsyncPools() {
    final Map<AsyncSchedulePool, AsyncProcessingScheduleServiceActor> asyncActors =
        new LinkedHashMap<>();
    final Map<AsyncSchedulePool, ProcessingScheduleServiceImpl> asyncActorServices =
        new LinkedHashMap<>();
    Arrays.stream(AsyncSchedulePool.values())
        .forEach(
            pool -> {
              final var tuple = createAsyncActor(pool);
              asyncActors.put(pool, tuple.getLeft());
              asyncActorServices.put(pool, tuple.getRight());
            });
    return new Tuple<>(asyncActors, asyncActorServices);
  }
}
