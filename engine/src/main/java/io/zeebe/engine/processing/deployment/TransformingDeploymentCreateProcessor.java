/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.deployment;

import static io.zeebe.engine.state.instance.TimerInstance.NO_ELEMENT_INSTANCE;

import io.zeebe.engine.processing.bpmn.behavior.TypedStreamWriterProxy;
import io.zeebe.engine.processing.common.CatchEventBehavior;
import io.zeebe.engine.processing.common.ExpressionProcessor;
import io.zeebe.engine.processing.common.ExpressionProcessor.EvaluationException;
import io.zeebe.engine.processing.common.Failure;
import io.zeebe.engine.processing.deployment.model.element.ExecutableCatchEventElement;
import io.zeebe.engine.processing.deployment.model.element.ExecutableStartEvent;
import io.zeebe.engine.processing.deployment.transform.DeploymentTransformer;
import io.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.zeebe.engine.processing.streamprocessor.sideeffect.SideEffectProducer;
import io.zeebe.engine.processing.streamprocessor.writers.EventApplyingStateWriter;
import io.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedStreamWriter;
import io.zeebe.engine.state.KeyGenerator;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.state.appliers.EventAppliers;
import io.zeebe.engine.state.immutable.TimerInstanceState;
import io.zeebe.engine.state.instance.TimerInstance;
import io.zeebe.engine.state.mutable.MutableEventScopeInstanceState;
import io.zeebe.engine.state.mutable.MutableWorkflowState;
import io.zeebe.model.bpmn.util.time.Timer;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.impl.record.value.deployment.WorkflowRecord;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.intent.DeploymentIntent;
import io.zeebe.util.Either;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;

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
  private final EventAppliers eventAppliers;
  private final TypedStreamWriterProxy typedStreamWriterProxy;
  private final StateWriter stateWriter;

  public TransformingDeploymentCreateProcessor(
      final ZeebeState zeebeState,
      final CatchEventBehavior catchEventBehavior,
      final ExpressionProcessor expressionProcessor) {
    eventAppliers = new EventAppliers(zeebeState);
    workflowState = zeebeState.getWorkflowState();
    eventScopeInstanceState = zeebeState.getEventScopeInstanceState();
    timerInstanceState = zeebeState.getTimerState();
    keyGenerator = zeebeState.getKeyGenerator();
    typedStreamWriterProxy = new TypedStreamWriterProxy();
    stateWriter = new EventApplyingStateWriter(typedStreamWriterProxy, eventAppliers);
    deploymentTransformer = new DeploymentTransformer(stateWriter, zeebeState, expressionProcessor);
    this.catchEventBehavior = catchEventBehavior;
    this.expressionProcessor = expressionProcessor;
  }

  @Override
  public void processRecord(
      final TypedRecord<DeploymentRecord> command,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter,
      final Consumer<SideEffectProducer> sideEffect) {
    typedStreamWriterProxy.wrap(streamWriter);
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
      streamWriter.appendFollowUpEvent(key, DeploymentIntent.CREATED, deploymentEvent);
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
