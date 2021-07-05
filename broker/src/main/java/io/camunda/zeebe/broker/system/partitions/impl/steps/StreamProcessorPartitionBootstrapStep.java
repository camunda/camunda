/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions.impl.steps;

import io.camunda.zeebe.broker.system.partitions.PartitionBootstrapContext;
import io.camunda.zeebe.broker.system.partitions.PartitionBootstrapStep;
import io.camunda.zeebe.engine.processing.streamprocessor.StreamProcessor;
import io.camunda.zeebe.engine.state.appliers.EventAppliers;
import io.camunda.zeebe.engine.state.mutable.MutableZeebeState;
import io.camunda.zeebe.util.sched.ActorControl;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.sched.future.CompletableActorFuture;

public class StreamProcessorPartitionBootstrapStep implements PartitionBootstrapStep {

  @Override
  public ActorFuture<PartitionBootstrapContext> open(final PartitionBootstrapContext context) {
    final var result = new CompletableActorFuture<PartitionBootstrapContext>();

    final StreamProcessor streamProcessor = createStreamProcessor(context);
    final ActorFuture<Void> openFuture = streamProcessor.openAsync(true);

    openFuture.onComplete(
        (nothing, error) -> {
          if (error != null) {
            result.completeExceptionally(error);
          } else {
            context.setStreamProcessor(streamProcessor);

            context
                .getComponentHealthMonitor()
                .registerComponent(streamProcessor.getName(), streamProcessor);
            result.complete(context);
          }
        });

    return result;
  }

  @Override
  public ActorFuture<PartitionBootstrapContext> close(final PartitionBootstrapContext context) {
    final var result = new CompletableActorFuture<PartitionBootstrapContext>();

    final ActorFuture<Void> future = context.getStreamProcessor().closeAsync();

    future.onComplete(
        (success, error) -> {
          if (error != null) {
            result.completeExceptionally(error);
          } else {
            context
                .getComponentHealthMonitor()
                .removeComponent(context.getStreamProcessor().getName());

            context.setStreamProcessor(null);

            result.complete(context);
          }
        });

    return result;
  }

  @Override
  public String getName() {
    return "StreamProcessor";
  }

  private StreamProcessor createStreamProcessor(final PartitionBootstrapContext state) {
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
