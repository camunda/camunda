/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment;

import static io.camunda.zeebe.engine.state.instance.TimerInstance.NO_ELEMENT_INSTANCE;

import io.camunda.zeebe.engine.processing.common.CatchEventBehavior;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor.EvaluationException;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.distribute.DeploymentDistributionBehavior;
import io.camunda.zeebe.engine.processing.deployment.distribute.DeploymentDistributor;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableCatchEventElement;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableStartEvent;
import io.camunda.zeebe.engine.processing.deployment.transform.DeploymentTransformer;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.KeyGenerator;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.immutable.TimerInstanceState;
import io.camunda.zeebe.engine.state.immutable.ZeebeState;
import io.camunda.zeebe.engine.state.instance.TimerInstance;
import io.camunda.zeebe.model.bpmn.util.time.Timer;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessMetadata;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.sched.ActorControl;
import java.util.List;
import org.agrona.DirectBuffer;

public final class DeploymentCreateProcessor implements TypedRecordProcessor<DeploymentRecord> {

  private static final String COULD_NOT_CREATE_TIMER_MESSAGE =
      "Expected to create timer for start event, but encountered the following error: %s";

  private final DeploymentTransformer deploymentTransformer;
  private final ProcessState processState;
  private final TimerInstanceState timerInstanceState;
  private final CatchEventBehavior catchEventBehavior;
  private final KeyGenerator keyGenerator;
  private final ExpressionProcessor expressionProcessor;
  private final StateWriter stateWriter;
  private final MessageStartEventSubscriptionManager messageStartEventSubscriptionManager;
  private final DeploymentDistributionBehavior deploymentDistributionBehavior;
  private final Writers writers;

  public DeploymentCreateProcessor(
      final ZeebeState zeebeState,
      final CatchEventBehavior catchEventBehavior,
      final ExpressionProcessor expressionProcessor,
      final int partitionsCount,
      final Writers writers,
      final ActorControl actor,
      final DeploymentDistributor deploymentDistributor,
      final KeyGenerator keyGenerator) {
    processState = zeebeState.getProcessState();
    timerInstanceState = zeebeState.getTimerState();
    this.keyGenerator = keyGenerator;
    stateWriter = writers.state();
    deploymentTransformer =
        new DeploymentTransformer(stateWriter, zeebeState, expressionProcessor, keyGenerator);
    this.catchEventBehavior = catchEventBehavior;
    this.expressionProcessor = expressionProcessor;
    messageStartEventSubscriptionManager =
        new MessageStartEventSubscriptionManager(
            processState, zeebeState.getMessageStartEventSubscriptionState(), keyGenerator);
    deploymentDistributionBehavior =
        new DeploymentDistributionBehavior(writers, partitionsCount, deploymentDistributor, actor);
    this.writers = writers;
  }

  @Override
  public void processRecord(final TypedRecord<DeploymentRecord> command) {

    final DeploymentRecord deploymentEvent = command.getValue();

    final boolean accepted = deploymentTransformer.transform(deploymentEvent);
    if (accepted) {
      final long key = keyGenerator.nextKey();

      try {
        createTimerIfTimerStartEvent(command, writers.command());
      } catch (final RuntimeException e) {
        final String reason = String.format(COULD_NOT_CREATE_TIMER_MESSAGE, e.getMessage());
        writers.response().writeRejectionOnCommand(command, RejectionType.PROCESSING_ERROR, reason);
        writers.rejection().appendRejection(command, RejectionType.PROCESSING_ERROR, reason);
        return;
      }

      writers
          .response()
          .writeEventOnCommand(key, DeploymentIntent.CREATED, deploymentEvent, command);

      stateWriter.appendFollowUpEvent(key, DeploymentIntent.CREATED, deploymentEvent);

      deploymentDistributionBehavior.distributeDeployment(deploymentEvent, key);
      messageStartEventSubscriptionManager.tryReOpenMessageStartEventSubscription(
          deploymentEvent, stateWriter);

    } else {
      writers
          .response()
          .writeRejectionOnCommand(
              command,
              deploymentTransformer.getRejectionType(),
              deploymentTransformer.getRejectionReason());
      writers
          .rejection()
          .appendRejection(
              command,
              deploymentTransformer.getRejectionType(),
              deploymentTransformer.getRejectionReason());
    }
  }

  private void createTimerIfTimerStartEvent(
      final TypedRecord<DeploymentRecord> record, final TypedCommandWriter streamWriter) {
    for (final ProcessMetadata processMetadata : record.getValue().processesMetadata()) {
      if (!processMetadata.isDuplicate()) {
        final List<ExecutableStartEvent> startEvents =
            processState.getProcessByKey(processMetadata.getKey()).getProcess().getStartEvents();

        unsubscribeFromPreviousTimers(streamWriter, processMetadata);
        subscribeToTimerStartEventIfExists(processMetadata, startEvents);
      }
    }
  }

  private void subscribeToTimerStartEventIfExists(
      final ProcessMetadata processMetadata, final List<ExecutableStartEvent> startEvents) {
    for (final ExecutableCatchEventElement startEvent : startEvents) {
      if (startEvent.isTimer()) {
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
            processMetadata.getKey(),
            startEvent.getId(),
            timerOrError.get());
      }
    }
  }

  private void unsubscribeFromPreviousTimers(
      final TypedCommandWriter streamWriter, final ProcessMetadata processRecord) {
    timerInstanceState.forEachTimerForElementInstance(
        NO_ELEMENT_INSTANCE,
        timer -> unsubscribeFromPreviousTimer(streamWriter, processRecord, timer));
  }

  private void unsubscribeFromPreviousTimer(
      final TypedCommandWriter streamWriter,
      final ProcessMetadata processMetadata,
      final TimerInstance timer) {
    final DirectBuffer timerBpmnId =
        processState.getProcessByKey(timer.getProcessDefinitionKey()).getBpmnProcessId();

    if (timerBpmnId.equals(processMetadata.getBpmnProcessIdBuffer())) {
      catchEventBehavior.unsubscribeFromTimerEvent(timer, streamWriter);
    }
  }
}
