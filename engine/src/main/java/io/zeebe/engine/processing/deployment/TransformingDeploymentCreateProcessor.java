/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.deployment;

import static io.zeebe.engine.state.instance.TimerInstance.NO_ELEMENT_INSTANCE;

import io.zeebe.engine.processing.common.CatchEventBehavior;
import io.zeebe.engine.processing.common.ExpressionProcessor;
import io.zeebe.engine.processing.common.ExpressionProcessor.EvaluationException;
import io.zeebe.engine.processing.common.Failure;
import io.zeebe.engine.processing.deployment.distribute.DeploymentDistributor;
import io.zeebe.engine.processing.deployment.distribute.PendingDeploymentDistribution;
import io.zeebe.engine.processing.deployment.model.element.ExecutableCatchEventElement;
import io.zeebe.engine.processing.deployment.model.element.ExecutableStartEvent;
import io.zeebe.engine.processing.deployment.transform.DeploymentTransformer;
import io.zeebe.engine.processing.streamprocessor.ReadonlyProcessingContext;
import io.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.zeebe.engine.processing.streamprocessor.sideeffect.SideEffectProducer;
import io.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedStreamWriter;
import io.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.zeebe.engine.state.KeyGenerator;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.state.immutable.TimerInstanceState;
import io.zeebe.engine.state.instance.TimerInstance;
import io.zeebe.engine.state.mutable.MutableDeploymentState;
import io.zeebe.engine.state.mutable.MutableEventScopeInstanceState;
import io.zeebe.engine.state.mutable.MutableWorkflowState;
import io.zeebe.model.bpmn.util.time.Timer;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentDistributionRecord;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.impl.record.value.deployment.WorkflowRecord;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.intent.DeploymentDistributionIntent;
import io.zeebe.protocol.record.intent.DeploymentIntent;
import io.zeebe.util.Either;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.future.ActorFuture;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class TransformingDeploymentCreateProcessor
    implements TypedRecordProcessor<DeploymentRecord> {

  private static final String COULD_NOT_CREATE_TIMER_MESSAGE =
      "Expected to create timer for start event, but encountered the following error: %s";
  private final DeploymentTransformer deploymentTransformer;
  private final MutableWorkflowState workflowState;
  private final MutableEventScopeInstanceState eventScopeInstanceState;
  private final TimerInstanceState timerInstanceState;
  private final CatchEventBehavior catchEventBehavior;
  private final KeyGenerator keyGenerator;
  private final ExpressionProcessor expressionProcessor;
  private final StateWriter stateWriter;
  private final List<Integer> partitions;
  private final DeploymentDistributionRecord deploymentDistributionRecord;
  private final MutableDeploymentState deploymentState;
  private final DeploymentDistributor deploymentDistributor;
  private final ActorControl actor;
  private final MessageStartEventSubscriptionManager messageStartEventSubscriptionManager;

  public TransformingDeploymentCreateProcessor(
      final ZeebeState zeebeState,
      final CatchEventBehavior catchEventBehavior,
      final ExpressionProcessor expressionProcessor,
      final int partitionsCount,
      final Writers writers,
      final ActorControl actor,
      final DeploymentDistributor deploymentDistributor) {
    workflowState = zeebeState.getWorkflowState();
    eventScopeInstanceState = zeebeState.getEventScopeInstanceState();
    timerInstanceState = zeebeState.getTimerState();
    keyGenerator = zeebeState.getKeyGenerator();
    stateWriter = writers.state();
    deploymentTransformer = new DeploymentTransformer(stateWriter, zeebeState, expressionProcessor);
    this.catchEventBehavior = catchEventBehavior;
    this.expressionProcessor = expressionProcessor;
    deploymentState = zeebeState.getDeploymentState();
    this.deploymentDistributor = deploymentDistributor;
    this.actor = actor;
    messageStartEventSubscriptionManager = new MessageStartEventSubscriptionManager(workflowState);

    // partitions
    partitions =
        IntStream.range(Protocol.START_PARTITION_ID, Protocol.START_PARTITION_ID + partitionsCount)
            .filter(partition -> partition != Protocol.DEPLOYMENT_PARTITION)
            .boxed()
            .collect(Collectors.toList());
    deploymentDistributionRecord = new DeploymentDistributionRecord();
  }

  @Override
  public void onRecovered(final ReadonlyProcessingContext context) {
    actor.submit(() -> reprocessPendingDeployments(context.getLogStreamWriter()));
  }

  private void reprocessPendingDeployments(final TypedStreamWriter logStreamWriter) {
    deploymentState.foreachPending(
        ((pendingDeploymentDistribution, key) -> {
          final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer();
          final DirectBuffer deployment = pendingDeploymentDistribution.getDeployment();
          buffer.putBytes(0, deployment, 0, deployment.capacity());

          distributeDeployment(
              key, pendingDeploymentDistribution.getSourcePosition(), buffer, logStreamWriter);
        }));
  }

  @Override
  public void processRecord(
      final long position,
      final TypedRecord<DeploymentRecord> command,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter,
      final Consumer<SideEffectProducer> sideEffect) {
    final DeploymentRecord deploymentEvent = command.getValue();

    final boolean accepted = deploymentTransformer.transform(deploymentEvent);
    if (accepted) {
      final long key = keyGenerator.nextKey();
      workflowState.putDeployment(deploymentEvent);

      try {
        createTimerIfTimerStartEvent(command, streamWriter);
      } catch (final RuntimeException e) {
        final String reason = String.format(COULD_NOT_CREATE_TIMER_MESSAGE, e.getMessage());
        responseWriter.writeRejectionOnCommand(command, RejectionType.PROCESSING_ERROR, reason);
        streamWriter.appendRejection(command, RejectionType.PROCESSING_ERROR, reason);
        return;
      }

      responseWriter.writeEventOnCommand(key, DeploymentIntent.CREATED, deploymentEvent, command);
      stateWriter.appendFollowUpEvent(key, DeploymentIntent.CREATED, deploymentEvent);

      deploymentDistribution(
          position, responseWriter, streamWriter, sideEffect, deploymentEvent, key);

      messageStartEventSubscriptionManager.tryReOpenMessageStartEventSubscription(
          deploymentEvent, streamWriter);

    } else {
      responseWriter.writeRejectionOnCommand(
          command,
          deploymentTransformer.getRejectionType(),
          deploymentTransformer.getRejectionReason());
      streamWriter.appendRejection(
          command,
          deploymentTransformer.getRejectionType(),
          deploymentTransformer.getRejectionReason());
    }
  }

  private void deploymentDistribution(
      final long position,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter,
      final Consumer<SideEffectProducer> sideEffect,
      final DeploymentRecord deploymentEvent,
      final long key) {
    partitions.forEach(
        partitionId -> {
          deploymentDistributionRecord.setPartition(partitionId);
          stateWriter.appendFollowUpEvent(
              key, DeploymentDistributionIntent.DISTRIBUTING, deploymentDistributionRecord);
          // todo(zell): push deployment to other partition
          //            deploymentDistributor.pushDeployment(key, partitionId, deploymentEvent);
        });

    if (partitions.isEmpty()) {
      stateWriter.appendFollowUpEvent(key, DeploymentIntent.FULLY_DISTRIBUTED, deploymentEvent);
    } else {
      // todo(zell): simplify for https://github.com/zeebe-io/zeebe/issues/6173
      // DEPLOYMENT DISTRIBUTION moved from distribute
      final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer();
      deploymentEvent.write(buffer, 0);

      final DirectBuffer bufferView = new UnsafeBuffer();
      bufferView.wrap(buffer, 0, deploymentEvent.getLength());

      // don't distribute the deployment on reprocessing because it may be already distributed
      // (#3124)
      // and it would lead to false-positives in the reprocessing issue detection (#5688)
      // instead, distribute the pending deployments after the reprocessing
      sideEffect.accept(
          () -> {
            distributeDeployment(key, position, bufferView, streamWriter);
            responseWriter.flush();
            return true;
          });
    }
  }

  private void distributeDeployment(
      final long key,
      final long position,
      final DirectBuffer buffer,
      final TypedStreamWriter logStreamWriter) {
    final ActorFuture<Void> pushDeployment =
        deploymentDistributor.pushDeployment(key, position, buffer);

    actor.runOnCompletion(
        pushDeployment, (aVoid, throwable) -> writeCreatingDeploymentCommand(logStreamWriter, key));
  }

  private void writeCreatingDeploymentCommand(
      final TypedStreamWriter logStreamWriter, final long deploymentKey) {
    final PendingDeploymentDistribution pendingDeploymentDistribution =
        deploymentDistributor.removePendingDeployment(deploymentKey);
    final DirectBuffer buffer = pendingDeploymentDistribution.getDeployment();
    final long sourcePosition = pendingDeploymentDistribution.getSourcePosition();

    final DeploymentRecord deploymentRecord = new DeploymentRecord();
    deploymentRecord.wrap(buffer);

    actor.runUntilDone(
        () -> {
          // we can re-use the write because we are running in the same actor/thread
          logStreamWriter.reset();
          logStreamWriter.configureSourceContext(sourcePosition);
          logStreamWriter.appendFollowUpEvent(
              deploymentKey, DeploymentIntent.FULLY_DISTRIBUTED, deploymentRecord);

          // todo(zell): this will move away on next PR's
          // https://github.com/zeebe-io/zeebe/issues/6173
          deploymentState.removeDeploymentRecord(deploymentKey);

          final long position = logStreamWriter.flush();
          if (position < 0) {
            actor.yield();
          } else {
            actor.done();
          }
        });
  }

  private void createTimerIfTimerStartEvent(
      final TypedRecord<DeploymentRecord> record, final TypedStreamWriter streamWriter) {
    for (final WorkflowRecord workflowRecord : record.getValue().workflows()) {
      final List<ExecutableStartEvent> startEvents =
          workflowState.getWorkflowByKey(workflowRecord.getKey()).getWorkflow().getStartEvents();
      boolean hasAtLeastOneTimer = false;

      unsubscribeFromPreviousTimers(streamWriter, workflowRecord);

      for (final ExecutableCatchEventElement startEvent : startEvents) {
        if (startEvent.isTimer()) {
          hasAtLeastOneTimer = true;

          // There are no variables when there is no process instance yet,
          // we use a negative scope key to indicate this
          final long scopeKey = -1L;
          final Either<Failure, Timer> timerOrError =
              startEvent.getTimerFactory().apply(expressionProcessor, scopeKey);
          if (timerOrError.isLeft()) {
            // todo(#4323): deal with this exceptional case without throwing an exception
            throw new EvaluationException(timerOrError.getLeft().getMessage());
          }
          catchEventBehavior.subscribeToTimerEvent(
              NO_ELEMENT_INSTANCE,
              NO_ELEMENT_INSTANCE,
              workflowRecord.getKey(),
              startEvent.getId(),
              timerOrError.get(),
              streamWriter);
        }
      }

      if (hasAtLeastOneTimer) {
        eventScopeInstanceState.createIfNotExists(workflowRecord.getKey(), Collections.emptyList());
      }
    }
  }

  private void unsubscribeFromPreviousTimers(
      final TypedStreamWriter streamWriter, final WorkflowRecord workflowRecord) {
    timerInstanceState.forEachTimerForElementInstance(
        NO_ELEMENT_INSTANCE,
        timer -> unsubscribeFromPreviousTimer(streamWriter, workflowRecord, timer));
  }

  private void unsubscribeFromPreviousTimer(
      final TypedStreamWriter streamWriter,
      final WorkflowRecord workflowRecord,
      final TimerInstance timer) {
    final DirectBuffer timerBpmnId =
        workflowState.getWorkflowByKey(timer.getWorkflowKey()).getBpmnProcessId();

    if (timerBpmnId.equals(workflowRecord.getBpmnProcessIdBuffer())) {
      catchEventBehavior.unsubscribeFromTimerEvent(timer, streamWriter);
    }
  }
}
