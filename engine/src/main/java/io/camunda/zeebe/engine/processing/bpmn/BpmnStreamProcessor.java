/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.bpmn;

import io.camunda.zeebe.engine.Loggers;
import io.camunda.zeebe.engine.metrics.ProcessEngineMetrics;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnIncidentBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnJobBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateTransitionBehavior;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowElement;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowNode;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutionListener;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.ExecutionListenerEventType;
import io.camunda.zeebe.stream.api.records.ExceededBatchRecordSizeException;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;

public final class BpmnStreamProcessor implements TypedRecordProcessor<ProcessInstanceRecord> {

  private static final Logger LOGGER = Loggers.PROCESS_PROCESSOR_LOGGER;

  private final BpmnElementContextImpl context = new BpmnElementContextImpl();

  private final ProcessState processState;
  private final BpmnElementProcessors processors;
  private final ProcessInstanceStateTransitionGuard stateTransitionGuard;
  private final BpmnStateTransitionBehavior stateTransitionBehavior;
  private final TypedRejectionWriter rejectionWriter;
  private final BpmnIncidentBehavior incidentBehavior;

  private final BpmnStateBehavior stateBehavior;

  private final BpmnJobBehavior jobBehavior;

  public BpmnStreamProcessor(
      final BpmnBehaviors bpmnBehaviors,
      final MutableProcessingState processingState,
      final Writers writers,
      final ProcessEngineMetrics processEngineMetrics) {
    processState = processingState.getProcessState();

    rejectionWriter = writers.rejection();
    incidentBehavior = bpmnBehaviors.incidentBehavior();
    stateTransitionGuard = bpmnBehaviors.stateTransitionGuard();
    stateTransitionBehavior =
        new BpmnStateTransitionBehavior(
            processingState.getKeyGenerator(),
            bpmnBehaviors.stateBehavior(),
            processEngineMetrics,
            this::getContainerProcessor,
            writers);
    processors = new BpmnElementProcessors(bpmnBehaviors, stateTransitionBehavior);
    stateBehavior = bpmnBehaviors.stateBehavior();
    jobBehavior = bpmnBehaviors.jobBehavior();
  }

  private BpmnElementContainerProcessor<ExecutableFlowElement> getContainerProcessor(
      final BpmnElementType elementType) {
    return processors.getContainerProcessor(elementType);
  }

  @Override
  public void processRecord(final TypedRecord<ProcessInstanceRecord> record) {

    // initialize
    final var intent = (ProcessInstanceIntent) record.getIntent();
    final var recordValue = record.getValue();

    context.init(record.getKey(), recordValue, intent);

    final var bpmnElementType = recordValue.getBpmnElementType();
    final var processor = processors.getProcessor(bpmnElementType);
    final ExecutableFlowElement element = getElement(recordValue, processor);

    stateTransitionGuard
        .isValidStateTransition(context, element)
        .ifRightOrLeft(
            ok -> {
              LOGGER.trace("Process process instance event [context: {}]", context);
              processEvent(intent, processor, element);
            },
            violation ->
                rejectionWriter.appendRejection(
                    record, RejectionType.INVALID_STATE, violation.getMessage()));
  }

  @Override
  public ProcessingError tryHandleError(
      final TypedRecord<ProcessInstanceRecord> command, final Throwable error) {
    if (error instanceof ExceededBatchRecordSizeException) {
      context.init(
          command.getKey(), command.getValue(), (ProcessInstanceIntent) command.getIntent());
      if (context.getBpmnElementType() != BpmnElementType.PROCESS) {
        // set element's state to what it was doing, this allows us to resolve the incident later
        final BpmnElementContext transitionedContext;
        transitionedContext =
            switch ((ProcessInstanceIntent) command.getIntent()) {
              case ACTIVATE_ELEMENT -> stateTransitionBehavior.transitionToActivating(context);
              case COMPLETE_ELEMENT -> stateTransitionBehavior.transitionToCompleting(context);
              case TERMINATE_ELEMENT -> stateTransitionBehavior.transitionToTerminating(context);
                // even though we don't know how to resolve this incident, we can still
                // raise it so the user can use modification to recover. The incident resolution
                // logic is smart enough to deal with this case. It will log an error due to
                // IllegalStateException.
              default -> context;
            };
        incidentBehavior.createIncident(
            new Failure(
                """
                Expected to process element '%s', but exceeded MAX_MESSAGE_SIZE limitation. \
                If you have large or many variables consider reducing these."""
                    .formatted(BufferUtil.bufferAsString(transitionedContext.getElementId())),
                ErrorType.MESSAGE_SIZE_EXCEEDED),
            transitionedContext);
        return ProcessingError.EXPECTED_ERROR;
      }
    }
    return ProcessingError.UNEXPECTED_ERROR;
  }

  private void processEvent(
      final ProcessInstanceIntent intent,
      final BpmnElementProcessor<ExecutableFlowElement> processor,
      final ExecutableFlowElement element) {

    switch (intent) {
      case ACTIVATE_ELEMENT:
        final var activatingContext = stateTransitionBehavior.transitionToActivating(context);
        stateTransitionBehavior
            .onElementActivating(element, activatingContext)
            .ifRightOrLeft(
                ok -> {
                  processor.onActivate(element, activatingContext);
                  // TODO: deal with incidents
                  // look for execution listeners
                  afterActivating(element, processor, activatingContext);
                },
                failure -> incidentBehavior.createIncident(failure, activatingContext));
        break;
      case COMPLETE_ELEMENT:
        final var completingContext = stateTransitionBehavior.transitionToCompleting(context);
        processor.onComplete(element, completingContext);
        // TODO: deal with incidents
        afterCompleting(element, processor, completingContext);
        break;
      case EXECUTION_LISTENER_COMPLETE:
        switch (stateBehavior.getElementInstance(context).getState()) {
          case ELEMENT_ACTIVATING ->
              onStartExecutionListenerComplete((ExecutableFlowNode) element, processor, context);
          case ELEMENT_COMPLETING ->
              onEndExecutionListenerComplete((ExecutableFlowNode) element, processor, context);
          default ->
              throw new UnsupportedOperationException("Unexpected element state: " + context);
        }
        break;
      case TERMINATE_ELEMENT:
        final var terminatingContext = stateTransitionBehavior.transitionToTerminating(context);
        processor.onTerminate(element, terminatingContext);
        break;
      default:
        throw new BpmnProcessingException(
            context,
            String.format(
                "Expected the processor '%s' to handle the event but the intent '%s' is not supported",
                processor.getClass(), intent));
    }
  }

  private void afterActivating(
      final ExecutableFlowElement element,
      final BpmnElementProcessor<ExecutableFlowElement> processor,
      final BpmnElementContext context) {

    if (element instanceof final ExecutableFlowNode node) {
      getExecutionListenersByEventType(node, ExecutionListenerEventType.START).stream()
          .findFirst()
          .ifPresentOrElse(
              firstStartEl -> createExecutionListenerJob(node, context, firstStartEl),
              () -> {
                processor.completeActivating(element, context);
                // stop the execution and wait for the job to be completed
              });
    }
    // other elements, like sequence flows, do not have execution listeners
    // assume that the element is activated already
  }

  private void afterCompleting(
      final ExecutableFlowElement element,
      final BpmnElementProcessor<ExecutableFlowElement> processor,
      final BpmnElementContext context) {

    if (element instanceof final ExecutableFlowNode node) {
      getExecutionListenersByEventType(node, ExecutionListenerEventType.END).stream()
          .findFirst()
          .ifPresentOrElse(
              firstEndEl -> createExecutionListenerJob(node, context, firstEndEl),
              () -> {
                processor.completeCompleting(element, context);
                // stop the execution and wait for the job to be completed
              });
    }
    // other elements, like sequence flows, do not have execution listeners
    // assume that the element is completed already
  }

  private List<ExecutionListener> getExecutionListenersByEventType(
      final ExecutableFlowNode element, final ExecutionListenerEventType eventType) {
    return element.getExecutionListeners().stream()
        .filter(el -> eventType == el.getEventType())
        .collect(Collectors.toList());
  }

  private void createExecutionListenerJob(
      final ExecutableFlowNode element,
      final BpmnElementContext context,
      final ExecutionListener listener) {
    jobBehavior
        .evaluateJobExpressions(listener.getJobWorkerProperties(), context)
        .ifRightOrLeft(
            elJobProperties ->
                jobBehavior.createNewExecutionListenerJob(
                    context, element, elJobProperties, listener.getEventType()),
            failure -> incidentBehavior.createIncident(failure, context));
  }

  public void onStartExecutionListenerComplete(
      final ExecutableFlowNode element,
      final BpmnElementProcessor<ExecutableFlowElement> processor,
      final BpmnElementContext context) {

    final String currentExecutionListenerType =
        stateBehavior.getElementInstance(context).getExecutionListenerType();

    final List<ExecutionListener> startExecutionListeners =
        getExecutionListenersByEventType(element, ExecutionListenerEventType.START);
    findNextExecutionListener(startExecutionListeners, currentExecutionListenerType)
        .ifPresentOrElse(
            el -> createExecutionListenerJob(element, context, el),
            () -> processor.completeActivating(element, context));
  }

  public void onEndExecutionListenerComplete(
      final ExecutableFlowNode element,
      final BpmnElementProcessor<ExecutableFlowElement> processor,
      final BpmnElementContext context) {

    final String currentExecutionListenerType =
        stateBehavior.getElementInstance(context).getExecutionListenerType();

    final List<ExecutionListener> endExecutionListeners =
        getExecutionListenersByEventType(element, ExecutionListenerEventType.END);
    findNextExecutionListener(endExecutionListeners, currentExecutionListenerType)
        .ifPresentOrElse(
            el -> createExecutionListenerJob(element, context, el),
            () -> processor.completeCompleting(element, context));
  }

  private Optional<ExecutionListener> findNextExecutionListener(
      final List<ExecutionListener> listeners, final String currentType) {
    return listeners.stream()
        .dropWhile(el -> !el.getJobWorkerProperties().getType().getExpression().equals(currentType))
        .skip(1) // skip current listener
        .findFirst();
  }

  private ExecutableFlowElement getElement(
      final ProcessInstanceRecord recordValue,
      final BpmnElementProcessor<ExecutableFlowElement> processor) {

    return processState.getFlowElement(
        recordValue.getProcessDefinitionKey(),
        recordValue.getTenantId(),
        recordValue.getElementIdBuffer(),
        processor.getType());
  }
}
