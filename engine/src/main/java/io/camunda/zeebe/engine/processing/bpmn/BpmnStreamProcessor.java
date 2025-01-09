/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.bpmn;

import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.Loggers;
import io.camunda.zeebe.engine.metrics.ProcessEngineMetrics;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnIncidentBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateTransitionBehavior;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowElement;
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
import io.camunda.zeebe.stream.api.records.ExceededBatchRecordSizeException;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;
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

  public BpmnStreamProcessor(
      final BpmnBehaviors bpmnBehaviors,
      final MutableProcessingState processingState,
      final Writers writers,
      final ProcessEngineMetrics processEngineMetrics,
      final EngineConfiguration config) {
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
    processors = new BpmnElementProcessors(bpmnBehaviors, stateTransitionBehavior, config);
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
                ok -> processor.onActivate(element, activatingContext),
                failure -> incidentBehavior.createIncident(failure, activatingContext));
        break;
      case COMPLETE_ELEMENT:
        final var completingContext = stateTransitionBehavior.transitionToCompleting(context);
        processor.onComplete(element, completingContext);
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
