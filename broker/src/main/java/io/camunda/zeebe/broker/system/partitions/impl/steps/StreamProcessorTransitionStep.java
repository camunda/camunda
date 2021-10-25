/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions.impl.steps;

import io.atomix.raft.RaftServer.Role;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionContext;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionStep;
import io.camunda.zeebe.engine.processing.streamprocessor.StreamProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.StreamProcessorMode;
import io.camunda.zeebe.engine.state.appliers.EventAppliers;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.sched.future.CompletableActorFuture;
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
    final var currentRole = context.getCurrentRole();
    final var streamprocessor = context.getStreamProcessor();

    if (streamprocessor != null
        && (shouldInstallOnTransition(targetRole, currentRole) || targetRole == Role.INACTIVE)) {
      context.getComponentHealthMonitor().removeComponent(streamprocessor.getName());
      final ActorFuture<Void> future = streamprocessor.closeAsync();
      future.onComplete(
          (success, error) -> {
            if (error == null) {
              context.setStreamProcessor(null);
            }
          });
      return future;
    } else {
      return CompletableActorFuture.completed(null);
    }
  }

  @Override
  public ActorFuture<Void> transitionTo(
      final PartitionTransitionContext context, final long term, final Role targetRole) {
    final var currentRole = context.getCurrentRole();

    if (shouldInstallOnTransition(targetRole, currentRole)
        || (context.getStreamProcessor() == null && targetRole != Role.INACTIVE)) {
      final StreamProcessor streamProcessor = streamProcessorCreator.apply(context, targetRole);
      final ActorFuture<Void> openFuture = streamProcessor.openAsync(!context.shouldProcess());
      final CompletableActorFuture<Void> future = new CompletableActorFuture<>();

      openFuture.onComplete(
          (nothing, err) -> {
            if (err == null) {
              context.setStreamProcessor(streamProcessor);

              // Have to pause/resume it here in case the state changed after streamProcessor was
              // created
              if (!context.shouldProcess()) {
                streamProcessor.pauseProcessing();
              } else {
                streamProcessor.resumeProcessing();
              }

              context
                  .getComponentHealthMonitor()
                  .registerComponent(streamProcessor.getName(), streamProcessor);
              future.complete(null);
            } else {
              future.completeExceptionally(err);
            }
          });

      return future;
    }

    return CompletableActorFuture.completed(null);
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
    return StreamProcessor.builder()
        .logStream(context.getLogStream())
        .actorSchedulingService(context.getActorSchedulingService())
        .zeebeDb(context.getZeebeDb())
        .eventApplierFactory(EventAppliers::new)
        .nodeId(context.getNodeId())
        .commandResponseWriter(context.getCommandResponseWriter())
        .listener(processedCommand -> context.getOnProcessedListener().accept(processedCommand))
        .streamProcessorFactory(context.getStreamProcessorFactory())
        .streamProcessorMode(streamProcessorMode)
        .build();
  }
}
