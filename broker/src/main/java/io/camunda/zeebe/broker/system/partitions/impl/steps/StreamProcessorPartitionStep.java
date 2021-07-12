/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions.impl.steps;

import io.camunda.zeebe.broker.system.partitions.PartitionBoostrapAndTransitionContextImpl;
import io.camunda.zeebe.broker.system.partitions.PartitionStep;
import io.camunda.zeebe.engine.processing.streamprocessor.StreamProcessor;
import io.camunda.zeebe.engine.state.appliers.EventAppliers;
import io.camunda.zeebe.engine.state.mutable.MutableZeebeState;
import io.camunda.zeebe.util.sched.ActorControl;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.sched.future.CompletableActorFuture;

public class StreamProcessorPartitionStep implements PartitionStep {

  @Override
  public ActorFuture<Void> open(final PartitionBoostrapAndTransitionContextImpl context) {
    final StreamProcessor streamProcessor = createStreamProcessor(context);
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

  @Override
  public ActorFuture<Void> close(final PartitionBoostrapAndTransitionContextImpl context) {
    context.getComponentHealthMonitor().removeComponent(context.getStreamProcessor().getName());
    final ActorFuture<Void> future = context.getStreamProcessor().closeAsync();
    context.setStreamProcessor(null);
    return future;
  }

  @Override
  public String getName() {
    return "StreamProcessor";
  }

  private StreamProcessor createStreamProcessor(
      final PartitionBoostrapAndTransitionContextImpl state) {
    return StreamProcessor.builder()
        .logStream(state.getLogStream())
        .actorSchedulingService(state.getActorSchedulingService())
        .zeebeDb(state.getZeebeDb())
        .eventApplierFactory(EventAppliers::new)
        .nodeId(state.getNodeId())
        .commandResponseWriter(state.getCommandResponseWriter())
        .onProcessedListener(state.getOnProcessedListener())
        .streamProcessorFactory(
            processingContext -> {
              final ActorControl actor = processingContext.getActor();
              final MutableZeebeState zeebeState = processingContext.getZeebeState();
              return state
                  .getTypedRecordProcessorsFactory()
                  .createTypedStreamProcessor(actor, zeebeState, processingContext);
            })
        .build();
  }
}
