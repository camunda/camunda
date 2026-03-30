/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn;

import io.camunda.zeebe.el.impl.StaticExpression;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.Loggers;
import io.camunda.zeebe.engine.metrics.ProcessEngineMetrics;
import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementProcessor.TransitionOutcome;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnIncidentBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnJobBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateTransitionBehavior;
import io.camunda.zeebe.engine.processing.common.EventTriggerBehavior;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowElement;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowNode;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutionListener;
import io.camunda.zeebe.engine.processing.deployment.model.element.JobWorkerProperties;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.processing.variable.VariableBehavior;
import io.camunda.zeebe.engine.state.globallistener.GlobalListenersState;
import io.camunda.zeebe.engine.state.immutable.EventScopeInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeExecutionListenerEventType;
import io.camunda.zeebe.protocol.impl.record.value.globallistener.GlobalListenerRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.GlobalListenerRecordValue;
import io.camunda.zeebe.stream.api.records.ExceededBatchRecordSizeException;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.slf4j.Logger;

@ExcludeAuthorizationCheck
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
  private final EventTriggerBehavior eventTriggerBehavior;
  private final VariableBehavior variableBehavior;
  private final EventScopeInstanceState eventScopeInstanceState;
  private final GlobalListenersState globalListenersState;

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
            bpmnBehaviors.jobBehavior(),
            bpmnBehaviors.eventSubscriptionBehavior(),
            bpmnBehaviors.incidentBehavior(),
            bpmnBehaviors.userTaskBehavior(),
            processEngineMetrics,
            this::getContainerProcessor,
            writers);
    processors =
        new BpmnElementProcessors(
            bpmnBehaviors, stateTransitionBehavior, processingState.getAsyncRequestState(), config);
    stateBehavior = bpmnBehaviors.stateBehavior();
    jobBehavior = bpmnBehaviors.jobBehavior();
    eventTriggerBehavior = bpmnBehaviors.eventTriggerBehavior();
    variableBehavior = bpmnBehaviors.variableBehavior();
    eventScopeInstanceState = processingState.getEventScopeInstanceState();
    globalListenersState = processingState.getGlobalListenersState();
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
            .flatMap(ok -> processor.onActivate(element, activatingContext))
            .flatMap(ok -> afterActivating(element, processor, activatingContext))
            .ifLeft(failure -> incidentBehavior.createIncident(failure, activatingContext));
        break;
      case COMPLETE_ELEMENT:
        final var completingContext = stateTransitionBehavior.transitionToCompleting(context);
        processor
            .onComplete(element, completingContext)
            .flatMap(ok -> afterCompleting(element, processor, completingContext))
            .ifLeft(failure -> incidentBehavior.createIncident(failure, completingContext));
        break;
      case TERMINATE_ELEMENT:
        final var terminatingContext = stateTransitionBehavior.transitionToTerminating(context);
        final var transitionOutcome = processor.onTerminate(element, terminatingContext);
        if (transitionOutcome == TransitionOutcome.CONTINUE) {
          processor.finalizeTermination(element, terminatingContext);
        }
        break;
      case COMPLETE_EXECUTION_LISTENER:
        final ProcessInstanceIntent elementState =
            stateBehavior.getElementInstance(context).getState();
        switch (elementState) {
          case ELEMENT_ACTIVATING ->
              onStartExecutionListenerComplete((ExecutableFlowNode) element, processor, context)
                  .ifLeft(failure -> incidentBehavior.createIncident(failure, context));
          case ELEMENT_COMPLETING ->
              onEndExecutionListenerComplete((ExecutableFlowNode) element, processor, context)
                  .ifLeft(failure -> incidentBehavior.createIncident(failure, context));
          default ->
              throw new BpmnProcessingException(
                  context, String.format("Unexpected element state: '%s'", elementState));
        }
        break;
      case CONTINUE_TERMINATING_ELEMENT:
        processor.finalizeTermination(element, context);
        break;
      default:
        throw new BpmnProcessingException(
            context,
            String.format(
                "Expected the processor '%s' to handle the event but the intent '%s' is not supported",
                processor.getClass(), intent));
    }
  }

  private Either<Failure, ?> afterActivating(
      final ExecutableFlowElement element,
      final BpmnElementProcessor<ExecutableFlowElement> processor,
      final BpmnElementContext context) {
    return processElementWithListeners(
        element,
        context,
        node -> getMergedExecutionListeners(node, context, ZeebeExecutionListenerEventType.start),
        processor::finalizeActivation);
  }

  private Either<Failure, ?> afterCompleting(
      final ExecutableFlowElement element,
      final BpmnElementProcessor<ExecutableFlowElement> processor,
      final BpmnElementContext context) {
    return processElementWithListeners(
        element,
        context,
        node -> getMergedExecutionListeners(node, context, ZeebeExecutionListenerEventType.end),
        processor::finalizeCompletion);
  }

  private Either<Failure, ?> processElementWithListeners(
      final ExecutableFlowElement element,
      final BpmnElementContext context,
      final Function<ExecutableFlowNode, List<ExecutionListener>> listenersGetter,
      final BiFunction<ExecutableFlowElement, BpmnElementContext, Either<Failure, ?>> finalizer) {

    if (!(element instanceof final ExecutableFlowNode node)) {
      // other elements, like sequence flows, do not have execution listeners
      // assume that the element is processed already
      return BpmnElementProcessor.SUCCESS;
    }

    final List<ExecutionListener> listeners = listenersGetter.apply(node);
    if (listeners.isEmpty()) {
      return finalizer.apply(element, context);
    }

    return createExecutionListenerJob(context, listeners.getFirst());
  }

  private Either<Failure, ?> createExecutionListenerJob(
      final BpmnElementContext context, final ExecutionListener listener) {
    return jobBehavior
        .evaluateJobExpressions(listener.getJobWorkerProperties(), context)
        .thenDo(
            elJobProperties ->
                jobBehavior.createNewExecutionListenerJob(context, elJobProperties, listener));
  }

  public Either<Failure, ?> onStartExecutionListenerComplete(
      final ExecutableFlowNode element,
      final BpmnElementProcessor<ExecutableFlowElement> processor,
      final BpmnElementContext context) {
    mergeVariablesOfExecutionListener(context, true);
    return onExecutionListenerComplete(
        element,
        context,
        node -> getMergedExecutionListeners(node, context, ZeebeExecutionListenerEventType.start),
        processor::finalizeActivation);
  }

  public Either<Failure, ?> onEndExecutionListenerComplete(
      final ExecutableFlowNode element,
      final BpmnElementProcessor<ExecutableFlowElement> processor,
      final BpmnElementContext context) {
    mergeVariablesOfExecutionListener(context, false);
    return onExecutionListenerComplete(
        element,
        context,
        node -> getMergedExecutionListeners(node, context, ZeebeExecutionListenerEventType.end),
        processor::finalizeCompletion);
  }

  private Either<Failure, ?> onExecutionListenerComplete(
      final ExecutableFlowNode element,
      final BpmnElementContext context,
      final Function<ExecutableFlowNode, List<ExecutionListener>> listenersGetter,
      final BiFunction<ExecutableFlowElement, BpmnElementContext, Either<Failure, ?>> finalizer) {

    final List<ExecutionListener> listeners = listenersGetter.apply(element);
    final int currentListenerIndex =
        stateBehavior.getElementInstance(context).getExecutionListenerIndex();
    final Optional<ExecutionListener> nextListener =
        findNextExecutionListener(listeners, currentListenerIndex);
    return nextListener.isPresent()
        ? createExecutionListenerJob(context, nextListener.get())
        : finalizer.apply(element, context);
  }

  private Optional<ExecutionListener> findNextExecutionListener(
      final List<ExecutionListener> listeners, final int nextListenerIndex) {
    return listeners.stream().skip(nextListenerIndex).findFirst();
  }

  private void mergeVariablesOfExecutionListener(
      final BpmnElementContext context, final boolean local) {
    Optional.ofNullable(eventScopeInstanceState.peekEventTrigger(context.getElementInstanceKey()))
        .ifPresent(
            eventTrigger -> {
              if (eventTrigger.getVariables().capacity() > 0) {
                final long scopeKey =
                    local || context.getFlowScopeKey() <= 0
                        ? context.getElementInstanceKey()
                        : context.getFlowScopeKey();

                variableBehavior.mergeLocalDocument(
                    scopeKey,
                    context.getProcessDefinitionKey(),
                    context.getProcessInstanceKey(),
                    context.getRootProcessInstanceKey(),
                    context.getBpmnProcessId(),
                    context.getTenantId(),
                    eventTrigger.getVariables());
              }

              eventTriggerBehavior.processEventTriggered(
                  eventTrigger.getEventKey(),
                  context.getProcessDefinitionKey(),
                  eventTrigger.getProcessInstanceKey(),
                  context.getTenantId(),
                  context.getElementInstanceKey(),
                  eventTrigger.getElementId());
            });
  }

  /**
   * Merges global execution listeners with BPMN-level execution listeners using the sandwich
   * pattern: global before-non-global listeners run first, then BPMN model listeners, then global
   * after-non-global listeners.
   *
   * <p>Global listeners are filtered by the element's BPMN type and the requested event type
   * (start/end). Uses the current (non-pinned) global listener config.
   */
  private List<ExecutionListener> getMergedExecutionListeners(
      final ExecutableFlowNode node,
      final BpmnElementContext context,
      final ZeebeExecutionListenerEventType eventType) {

    final var bpmnListeners =
        eventType == ZeebeExecutionListenerEventType.start
            ? node.getStartExecutionListeners()
            : node.getEndExecutionListeners();

    final var currentConfig = globalListenersState.getCurrentConfig();
    if (currentConfig == null) {
      return bpmnListeners;
    }

    final var globalExecutionListeners = currentConfig.getExecutionListeners();
    if (globalExecutionListeners.isEmpty()) {
      return bpmnListeners;
    }

    final var elementTypeName = resolveElementTypeName(context.getBpmnElementType());
    if (elementTypeName == null) {
      return bpmnListeners;
    }

    final var eventTypeStr = eventType == ZeebeExecutionListenerEventType.start ? "START" : "END";

    final List<ExecutionListener> beforeNonGlobal = new ArrayList<>();
    final List<ExecutionListener> afterNonGlobal = new ArrayList<>();

    for (final var globalListener : globalExecutionListeners) {
      if (!matchesElementType(globalListener, elementTypeName)) {
        continue;
      }
      if (!matchesEventType(globalListener, eventTypeStr)) {
        continue;
      }
      final var converted = toExecutionListenerModel(globalListener, eventType);
      if (globalListener.isAfterNonGlobal()) {
        afterNonGlobal.add(converted);
      } else {
        beforeNonGlobal.add(converted);
      }
    }

    if (beforeNonGlobal.isEmpty() && afterNonGlobal.isEmpty()) {
      return bpmnListeners;
    }

    final List<ExecutionListener> merged = new ArrayList<>();
    merged.addAll(beforeNonGlobal);
    merged.addAll(bpmnListeners);
    merged.addAll(afterNonGlobal);
    return merged;
  }

  private boolean matchesElementType(
      final GlobalListenerRecordValue globalListener, final String elementTypeName) {
    final var targetTypes = ((GlobalListenerRecord) globalListener).resolveTargetElementTypes();
    return targetTypes.contains(elementTypeName);
  }

  private boolean matchesEventType(
      final GlobalListenerRecordValue globalListener, final String eventType) {
    final var eventTypes = globalListener.getEventTypes();
    return eventTypes.contains(GlobalListenerRecord.ALL_EVENT_TYPES)
        || eventTypes.contains(eventType);
  }

  private ExecutionListener toExecutionListenerModel(
      final GlobalListenerRecordValue globalListener,
      final ZeebeExecutionListenerEventType eventType) {
    final var listener = new ExecutionListener();
    listener.setEventType(eventType);

    final var jobProperties = new JobWorkerProperties();
    jobProperties.setType(new StaticExpression(globalListener.getType()));
    jobProperties.setRetries(new StaticExpression(String.valueOf(globalListener.getRetries())));
    listener.setJobWorkerProperties(jobProperties);

    return listener;
  }

  /** Maps a BpmnElementType to the camelCase string used in global listener element type config. */
  private String resolveElementTypeName(final BpmnElementType bpmnElementType) {
    return switch (bpmnElementType) {
      case EVENT_SUB_PROCESS -> "eventSubProcess";
      case MULTI_INSTANCE_BODY -> "multiInstanceBody";
      default -> bpmnElementType.getElementTypeName().orElse(null);
    };
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
