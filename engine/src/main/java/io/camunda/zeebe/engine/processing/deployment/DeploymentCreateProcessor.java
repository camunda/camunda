/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment;

import static io.camunda.zeebe.engine.state.instance.TimerInstance.NO_ELEMENT_INSTANCE;

import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.common.CatchEventBehavior;
import io.camunda.zeebe.engine.processing.common.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor.EvaluationException;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.distribute.DeploymentDistributionBehavior;
import io.camunda.zeebe.engine.processing.deployment.distribute.DeploymentDistributionCommandSender;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableCatchEventElement;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableStartEvent;
import io.camunda.zeebe.engine.processing.deployment.transform.DeploymentTransformer;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.TimerInstanceState;
import io.camunda.zeebe.engine.state.instance.TimerInstance;
import io.camunda.zeebe.model.bpmn.util.time.Timer;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessMetadata;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.FeatureFlags;
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
  private final StartEventSubscriptionManager startEventSubscriptionManager;
  private final DeploymentDistributionBehavior deploymentDistributionBehavior;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final CommandDistributionBehavior distributionBehavior;

  public DeploymentCreateProcessor(
      final ProcessingState processingState,
      final BpmnBehaviors bpmnBehaviors,
      final int partitionsCount,
      final Writers writers,
      final DeploymentDistributionCommandSender deploymentDistributionCommandSender,
      final KeyGenerator keyGenerator,
      final FeatureFlags featureFlags,
      final CommandDistributionBehavior distributionBehavior) {
    processState = processingState.getProcessState();
    timerInstanceState = processingState.getTimerState();
    this.keyGenerator = keyGenerator;
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    catchEventBehavior = bpmnBehaviors.catchEventBehavior();
    expressionProcessor = bpmnBehaviors.expressionBehavior();
    this.distributionBehavior = distributionBehavior;
    deploymentTransformer =
        new DeploymentTransformer(
            stateWriter, processingState, expressionProcessor, keyGenerator, featureFlags);
    startEventSubscriptionManager =
        new StartEventSubscriptionManager(processingState, keyGenerator);
    deploymentDistributionBehavior =
        new DeploymentDistributionBehavior(
            writers, partitionsCount, deploymentDistributionCommandSender);
  }

  @Override
  public void processRecord(final TypedRecord<DeploymentRecord> command) {
    final DeploymentRecord deploymentEvent = command.getValue();

    if (!command.isCommandDistributed()) {
      transformAndDistributeDeployment(command);
    } else {
      // TODO write process commands
      // TODO write dmn commands
      // TODO ack distribution
    }

    // manage the top-level start event subscriptions except for timers
    startEventSubscriptionManager.tryReOpenStartEventSubscription(deploymentEvent, stateWriter);
  }

  @Override
  public ProcessingError tryHandleError(
      final TypedRecord<DeploymentRecord> command, final Throwable error) {
    if (error instanceof ResourceTransformationFailedException exception) {
      rejectionWriter.appendRejection(
          command, RejectionType.INVALID_ARGUMENT, exception.getMessage());
      responseWriter.writeRejectionOnCommand(
          command, RejectionType.INVALID_ARGUMENT, exception.getMessage());
      return ProcessingError.EXPECTED_ERROR;
    } else if (error instanceof TimerCreationFailedException exception) {
      rejectionWriter.appendRejection(
          command, RejectionType.PROCESSING_ERROR, exception.getMessage());
      responseWriter.writeRejectionOnCommand(
          command, RejectionType.PROCESSING_ERROR, exception.getMessage());
      return ProcessingError.EXPECTED_ERROR;
    }

    return ProcessingError.UNEXPECTED_ERROR;
  }

  private void transformAndDistributeDeployment(final TypedRecord<DeploymentRecord> command) {
    final DeploymentRecord deploymentEvent = command.getValue();
    // Note: transforming a resource will also write the CREATE events for said resource
    final Either<Failure, Void> result = deploymentTransformer.transform(deploymentEvent);

    if (result.isLeft()) {
      throw new ResourceTransformationFailedException(result.getLeft().getMessage());
    }

    try {
      createTimerIfTimerStartEvent(command);
    } catch (final RuntimeException e) {
      final String reason = String.format(COULD_NOT_CREATE_TIMER_MESSAGE, e.getMessage());
      throw new TimerCreationFailedException(reason);
    }

    final long key = keyGenerator.nextKey();
    responseWriter.writeEventOnCommand(key, DeploymentIntent.CREATED, deploymentEvent, command);
    stateWriter.appendFollowUpEvent(key, DeploymentIntent.CREATED, deploymentEvent);

    // TODO different distributor
    deploymentDistributionBehavior.distributeDeployment(deploymentEvent, key);
  }

  private void createTimerIfTimerStartEvent(final TypedRecord<DeploymentRecord> record) {
    for (final ProcessMetadata processMetadata : record.getValue().processesMetadata()) {
      if (!processMetadata.isDuplicate()) {
        final List<ExecutableStartEvent> startEvents =
            processState.getProcessByKey(processMetadata.getKey()).getProcess().getStartEvents();

        unsubscribeFromPreviousTimers(processMetadata);
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

  private void unsubscribeFromPreviousTimers(final ProcessMetadata processRecord) {
    timerInstanceState.forEachTimerForElementInstance(
        NO_ELEMENT_INSTANCE, timer -> unsubscribeFromPreviousTimer(processRecord, timer));
  }

  private void unsubscribeFromPreviousTimer(
      final ProcessMetadata processMetadata, final TimerInstance timer) {
    final DirectBuffer timerBpmnId =
        processState.getProcessByKey(timer.getProcessDefinitionKey()).getBpmnProcessId();

    if (timerBpmnId.equals(processMetadata.getBpmnProcessIdBuffer())) {
      catchEventBehavior.unsubscribeFromTimerEvent(timer);
    }
  }

  /**
   * Exception that can be thrown during processing of a command, in case the resource cannot be
   * transformed successfully. This allows the platform to roll back any changes the engine made.
   * This exception can be handled by the processor in {@link #tryHandleError(TypedRecord,
   * Throwable)}.
   */
  private static final class ResourceTransformationFailedException extends RuntimeException {

    private ResourceTransformationFailedException(final String message) {
      super(message);
    }
  }

  /**
   * Exception can be thrown during processing of a command, in case a timer start event could not
   * be created. This exception is handled in the {@link #tryHandleError(TypedRecord, Throwable)}
   * method.
   */
  private static final class TimerCreationFailedException extends RuntimeException {

    public TimerCreationFailedException(final String message) {
      super(message);
    }
  }
}
