/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment;

import static io.camunda.zeebe.engine.state.instance.TimerInstance.NO_ELEMENT_INSTANCE;
import static io.camunda.zeebe.util.buffer.BufferUtil.wrapArray;
import static java.util.function.Predicate.not;

import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.common.CatchEventBehavior;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor.EvaluationException;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableCatchEventElement;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableStartEvent;
import io.camunda.zeebe.engine.processing.deployment.transform.DeploymentTransformer;
import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.DistributedTypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.DecisionState;
import io.camunda.zeebe.engine.state.immutable.FormState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.TimerInstanceState;
import io.camunda.zeebe.engine.state.instance.TimerInstance;
import io.camunda.zeebe.model.bpmn.util.time.Timer;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRequirementsMetadataRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRequirementsRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.FormMetadataRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.FormRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessMetadata;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.DecisionIntent;
import io.camunda.zeebe.protocol.record.intent.DecisionRequirementsIntent;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.FormIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.deployment.DeploymentResource;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.FeatureFlags;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import org.agrona.DirectBuffer;

public final class DeploymentCreateProcessor
    implements DistributedTypedRecordProcessor<DeploymentRecord> {

  private static final String COULD_NOT_CREATE_TIMER_MESSAGE =
      "Expected to create timer for start event, but encountered the following error: %s";

  private final DeploymentTransformer deploymentTransformer;
  private final ProcessState processState;
  private final DecisionState decisionState;
  private final FormState formState;
  private final TimerInstanceState timerInstanceState;
  private final CatchEventBehavior catchEventBehavior;
  private final KeyGenerator keyGenerator;
  private final ExpressionProcessor expressionProcessor;
  private final StateWriter stateWriter;
  private final StartEventSubscriptionManager startEventSubscriptionManager;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final CommandDistributionBehavior distributionBehavior;

  public DeploymentCreateProcessor(
      final ProcessingState processingState,
      final BpmnBehaviors bpmnBehaviors,
      final Writers writers,
      final KeyGenerator keyGenerator,
      final FeatureFlags featureFlags,
      final CommandDistributionBehavior distributionBehavior,
      final EngineConfiguration config) {
    processState = processingState.getProcessState();
    decisionState = processingState.getDecisionState();
    formState = processingState.getFormState();
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
            stateWriter, processingState, expressionProcessor, keyGenerator, featureFlags, config);
    startEventSubscriptionManager =
        new StartEventSubscriptionManager(processingState, keyGenerator, stateWriter);
  }

  @Override
  public void processNewCommand(final TypedRecord<DeploymentRecord> command) {
    transformAndDistributeDeployment(command);
    // manage the top-level start event subscriptions except for timers
    startEventSubscriptionManager.tryReOpenStartEventSubscription(command.getValue());
  }

  @Override
  public void processDistributedCommand(final TypedRecord<DeploymentRecord> command) {
    processDistributedRecord(command);
    // manage the top-level start event subscriptions except for timers
    startEventSubscriptionManager.tryReOpenStartEventSubscription(command.getValue());
  }

  @Override
  public ProcessingError tryHandleError(
      final TypedRecord<DeploymentRecord> command, final Throwable error) {
    // Make sure the cache does not contain any leftovers from this run (by hard resetting)
    if (command.getValue().hasBpmnResources()) {
      processState.clearCache();
    }
    if (command.getValue().hasDmnResources()) {
      decisionState.clearCache();
    }
    if (command.getValue().hasForms()) {
      formState.clearCache();
    }

    if (error instanceof final ResourceTransformationFailedException exception) {
      rejectionWriter.appendRejection(
          command, RejectionType.INVALID_ARGUMENT, exception.getMessage());
      responseWriter.writeRejectionOnCommand(
          command, RejectionType.INVALID_ARGUMENT, exception.getMessage());
      return ProcessingError.EXPECTED_ERROR;
    } else if (error instanceof final TimerCreationFailedException exception) {
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
    final long key = keyGenerator.nextKey();
    deploymentEvent.setKey(key);

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

    final var recordWithoutResource = createDeploymentWithoutResources(deploymentEvent);
    responseWriter.writeEventOnCommand(
        key, DeploymentIntent.CREATED, recordWithoutResource, command);
    stateWriter.appendFollowUpEvent(key, DeploymentIntent.CREATED, recordWithoutResource);

    distributionBehavior.distributeCommand(key, command);
  }

  private void processDistributedRecord(final TypedRecord<DeploymentRecord> command) {
    final var deploymentEvent = command.getValue();
    createBpmnResources(deploymentEvent);
    createDmnResources(deploymentEvent);
    createFormResources(deploymentEvent);
    final var recordWithoutResource = createDeploymentWithoutResources(deploymentEvent);
    stateWriter.appendFollowUpEvent(
        command.getKey(), DeploymentIntent.CREATED, recordWithoutResource);
    distributionBehavior.acknowledgeCommand(command);
  }

  private void createBpmnResources(final DeploymentRecord deploymentEvent) {
    deploymentEvent.processesMetadata().stream()
        .filter(not(ProcessMetadata::isDuplicate))
        .forEach(
            metadata -> {
              for (final DeploymentResource resource : deploymentEvent.getResources()) {
                final var resourceChecksum =
                    deploymentTransformer.getChecksum(resource.getResource());
                if (resourceChecksum.equals(metadata.getChecksumBuffer())) {
                  stateWriter.appendFollowUpEvent(
                      metadata.getKey(),
                      ProcessIntent.CREATED,
                      new ProcessRecord().wrap(metadata, resource.getResource()));
                }
              }
            });
  }

  private void createDmnResources(final DeploymentRecord deploymentEvent) {
    deploymentEvent.decisionRequirementsMetadata().stream()
        .filter(not(DecisionRequirementsMetadataRecord::isDuplicate))
        .map(drg -> createDrgRecord(deploymentEvent, drg))
        .forEach(
            decisionRequirementsRecord ->
                stateWriter.appendFollowUpEvent(
                    decisionRequirementsRecord.getDecisionRequirementsKey(),
                    DecisionRequirementsIntent.CREATED,
                    decisionRequirementsRecord));
    deploymentEvent.decisionsMetadata().stream()
        .filter(not(DecisionRecord::isDuplicate))
        .forEach(
            (record) ->
                stateWriter.appendFollowUpEvent(
                    record.getDecisionKey(), DecisionIntent.CREATED, record));
  }

  private void createFormResources(final DeploymentRecord deploymentEvent) {
    deploymentEvent.formMetadata().stream()
        .filter(not(FormMetadataRecord::isDuplicate))
        .forEach(
            metadata -> {
              for (final DeploymentResource resource : deploymentEvent.getResources()) {
                if (resource.getResourceName().equals(metadata.getResourceName())) {
                  stateWriter.appendFollowUpEvent(
                      metadata.getFormKey(),
                      FormIntent.CREATED,
                      new FormRecord().wrap(metadata, resource.getResource()));
                }
              }
            });
  }

  /**
   * Create a copy of the provided deployment record without resource
   *
   * @param deploymentEvent the record to clone
   * @return a copy of the {@link DeploymentRecord} provided without the resources
   */
  private DeploymentRecord createDeploymentWithoutResources(
      final DeploymentRecord deploymentEvent) {
    final var copyRecord = new DeploymentRecord();
    copyRecord.wrap(BufferUtil.createCopy(deploymentEvent));
    copyRecord.resetResources();
    return copyRecord;
  }

  private void createTimerIfTimerStartEvent(final TypedRecord<DeploymentRecord> record) {
    for (final ProcessMetadata processMetadata : record.getValue().processesMetadata()) {
      if (!processMetadata.isDuplicate()) {
        final List<ExecutableStartEvent> startEvents =
            processState
                .getProcessByKeyAndTenant(processMetadata.getKey(), processMetadata.getTenantId())
                .getProcess()
                .getStartEvents();

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
            processMetadata.getTenantId(),
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
        processState
            .getProcessByKeyAndTenant(timer.getProcessDefinitionKey(), timer.getTenantId())
            .getBpmnProcessId();

    if (timerBpmnId.equals(processMetadata.getBpmnProcessIdBuffer())
        && timer.getTenantId().equals(processMetadata.getTenantId())) {
      catchEventBehavior.unsubscribeFromTimerEvent(timer);
    }
  }

  private DecisionRequirementsRecord createDrgRecord(
      final DeploymentRecord deploymentEvent, final DecisionRequirementsMetadataRecord drg) {
    final var resource =
        deploymentEvent.getResources().stream()
            .filter(r -> r.getResourceName().equals(drg.getResourceName()))
            .map(DeploymentResource::getResource)
            .map(BufferUtil::wrapArray)
            .findFirst()
            .orElseThrow(() -> new NoSuchResourceException(drg.getResourceName()));
    return new DecisionRequirementsRecord()
        .setDecisionRequirementsKey(drg.getDecisionRequirementsKey())
        .setDecisionRequirementsId(drg.getDecisionRequirementsId())
        .setDecisionRequirementsVersion(drg.getDecisionRequirementsVersion())
        .setDecisionRequirementsName(drg.getDecisionRequirementsName())
        .setNamespace(drg.getNamespace())
        .setResourceName(drg.getResourceName())
        .setChecksum(wrapArray(drg.getChecksum()))
        .setResource(resource)
        .setTenantId(drg.getTenantId());
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

  private static final class NoSuchResourceException extends IllegalStateException {
    private NoSuchResourceException(final String resourceName) {
      super(
          String.format(
              "Expected to find resource '%s' in deployment but not found", resourceName));
    }
  }
}
