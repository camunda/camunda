/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions.impl.steps;

import io.atomix.raft.RaftServer.Role;
import io.camunda.zeebe.broker.engine.impl.BoundedScheduledCommandCache;
import io.camunda.zeebe.broker.engine.impl.ScheduledCommandCacheMetrics.BoundedCommandCacheMetrics;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionContext;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionStep;
import io.camunda.zeebe.engine.Engine;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.RecordProcessor;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.stream.impl.SkipPositionsFilter;
import io.camunda.zeebe.stream.impl.StreamProcessor;
import io.camunda.zeebe.stream.impl.StreamProcessorMode;
import java.util.List;
import java.util.function.BiFunction;

public final class StreamProcessorTransitionStep implements PartitionTransitionStep {

  private final BiFunction<PartitionTransitionContext, Role, StreamProcessor>
      streamProcessorCreator;

  public StreamProcessorTransitionStep() {
    this(StreamProcessorTransitionStep::createStreamProcessor);
  }

  // Used for testing
  public StreamProcessorTransitionStep(
      final BiFunction<PartitionTransitionContext, Role, StreamProcessor> streamProcessorCreator) {
    this.streamProcessorCreator = streamProcessorCreator;
  }

  @Override
  public void onNewRaftRole(final PartitionTransitionContext context, final Role newRole) {
    final var currentRole = context.getCurrentRole();
    final var streamprocessor = context.getStreamProcessor();
    if (streamprocessor == null) {
      return;
    }
    if (shouldInstallOnTransition(newRole, currentRole) || newRole == Role.INACTIVE) {
      // Right now this step has no value. But in future, we hope to remove `prepareTransition`
      // step. Then we will be closing the steps asynchronously. At that time we need to stop/pause
      // the services until the transition is complete.
      streamprocessor.pauseProcessing();
    }
  }

  @Override
  public ActorFuture<Void> prepareTransition(
      final PartitionTransitionContext context, final long term, final Role targetRole) {
    final var concurrencyControl = context.getConcurrencyControl();
    final var currentRole = context.getCurrentRole();
    final var streamprocessor = context.getStreamProcessor();

    if (streamprocessor != null
        && (shouldInstallOnTransition(targetRole, currentRole) || targetRole == Role.INACTIVE)) {
      context.getComponentHealthMonitor().removeComponent(streamprocessor);
      final ActorFuture<Void> future = streamprocessor.closeAsync();
      future.onComplete(
          (success, error) -> {
            if (error == null) {
              context.setStreamProcessor(null);
            }
          });
      return future;
    } else {
      return concurrencyControl.createCompletedFuture();
    }
  }

  @Override
  public ActorFuture<Void> transitionTo(
      final PartitionTransitionContext context, final long term, final Role targetRole) {
    final var currentRole = context.getCurrentRole();
    final var concurrencyControl = context.getConcurrencyControl();

    if (shouldInstallOnTransition(targetRole, currentRole)
        || (context.getStreamProcessor() == null && targetRole != Role.INACTIVE)) {
      final StreamProcessor streamProcessor = streamProcessorCreator.apply(context, targetRole);
      context.setStreamProcessor(streamProcessor);
      final ActorFuture<Void> openFuture = streamProcessor.openAsync(!context.shouldProcess());
      final ActorFuture<Void> future = concurrencyControl.createFuture();

      openFuture.onComplete(
          (nothing, err) -> {
            if (err == null) {
              // Have to pause/resume it here in case the state changed after streamProcessor was
              // created
              if (!context.shouldProcess()) {
                streamProcessor.pauseProcessing();
              } else {
                streamProcessor.resumeProcessing();
              }

              context.getComponentHealthMonitor().registerComponent(streamProcessor);
              future.complete(null);
            } else {
              future.completeExceptionally(err);
            }
          });

      return future;
    }

    return concurrencyControl.createCompletedFuture();
  }

  @Override
  public String getName() {
    return "StreamProcessor";
  }

  private boolean shouldInstallOnTransition(final Role newRole, final Role currentRole) {
    return newRole == Role.LEADER
        || (newRole == Role.FOLLOWER && currentRole != Role.CANDIDATE)
        || (newRole == Role.CANDIDATE && currentRole != Role.FOLLOWER);
  }

  private static StreamProcessor createStreamProcessor(
      final PartitionTransitionContext context, final Role targetRole) {
    final StreamProcessorMode streamProcessorMode =
        targetRole == Role.LEADER ? StreamProcessorMode.PROCESSING : StreamProcessorMode.REPLAY;

    final var experimentalCfg = context.getBrokerCfg().getExperimental();
    final var engineCfg = experimentalCfg.getEngine().createEngineConfiguration();

    final var engine = new Engine(context.getTypedRecordProcessorFactory(), engineCfg);
    final List<RecordProcessor> recordProcessors =
        List.of(engine, context.getCheckpointProcessor());
    final var scheduledCommandCache =
        BoundedScheduledCommandCache.ofIntent(
            new BoundedCommandCacheMetrics(context.getPartitionId()),
            TimerIntent.TRIGGER,
            JobIntent.TIME_OUT,
            JobIntent.RECUR_AFTER_BACKOFF,
            MessageIntent.EXPIRE);
    final var processingFilter =
        SkipPositionsFilter.of(context.getBrokerCfg().getProcessing().skipPositions());

    return StreamProcessor.builder()
        .meterRegistry(context.getPartitionMeterRegistry())
        .logStream(context.getLogStream())
        .actorSchedulingService(context.getActorSchedulingService())
        .zeebeDb(context.getZeebeDb())
        .recordProcessors(recordProcessors)
        .nodeId(context.getNodeId())
        .commandResponseWriter(context.getCommandApiService().newCommandResponseWriter())
        .maxCommandsInBatch(context.getBrokerCfg().getProcessing().getMaxCommandsInBatch())
        .setEnableAsyncScheduledTasks(
            context.getBrokerCfg().getProcessing().isEnableAsyncScheduledTasks())
        .setScheduledTaskCheckInterval(
            context.getBrokerCfg().getProcessing().getScheduledTaskCheckInterval())
        .processingFilter(processingFilter)
        .listener(
            processedCommand ->
                context.getLogStream().getFlowControl().onProcessed(processedCommand.getPosition()))
        .addLifecycleListener(
            new StreamProcessorLifecycleAware() {
              @Override
              public void onRecovered(final ReadonlyStreamProcessorContext ignored) {
                context.getCommandApiService().onRecovered(context.getPartitionId());
              }

              @Override
              public void onPaused() {
                context.getCommandApiService().onPaused(context.getPartitionId());
              }

              @Override
              public void onResumed() {
                context.getCommandApiService().onResumed(context.getPartitionId());
              }
            })
        .streamProcessorMode(streamProcessorMode)
        .partitionCommandSender(context.getPartitionCommandSender())
        .scheduledCommandCache(scheduledCommandCache)
        .clock(context.getStreamClock())
        .build();
  }
}
