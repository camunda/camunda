/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.bpmn.behavior;

import io.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.zeebe.engine.processing.bpmn.BpmnProcessingException;
import io.zeebe.engine.processing.common.CatchEventBehavior;
import io.zeebe.engine.processing.common.EventTriggerBehavior;
import io.zeebe.engine.processing.common.ExpressionProcessor.EvaluationException;
import io.zeebe.engine.processing.common.Failure;
import io.zeebe.engine.processing.deployment.model.element.ExecutableCatchEventSupplier;
import io.zeebe.engine.processing.deployment.model.element.ExecutableFlowElement;
import io.zeebe.engine.processing.message.MessageCorrelationKeyException;
import io.zeebe.engine.processing.message.MessageNameException;
import io.zeebe.engine.processing.streamprocessor.sideeffect.SideEffects;
import io.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.zeebe.engine.state.KeyGenerator;
import io.zeebe.engine.state.immutable.ProcessState;
import io.zeebe.engine.state.instance.EventTrigger;
import io.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.zeebe.engine.state.mutable.MutableEventScopeInstanceState;
import io.zeebe.engine.state.mutable.MutableZeebeState;
import io.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import io.zeebe.protocol.record.value.ErrorType;
import io.zeebe.util.Either;
import java.util.Optional;

public final class BpmnEventSubscriptionBehavior {

  private static final String NO_PROCESS_FOUND_MESSAGE =
      "Expected to create an instance of process with key '%d', but no such process was found";

  private final ProcessInstanceRecord eventRecord = new ProcessInstanceRecord();

  private final BpmnStateBehavior stateBehavior;
  private final MutableEventScopeInstanceState eventScopeInstanceState;
  private final MutableElementInstanceState elementInstanceState;
  private final CatchEventBehavior catchEventBehavior;

  private final StateWriter stateWriter;
  private final SideEffects sideEffects;

  private final KeyGenerator keyGenerator;
  private final ProcessState processState;
  private final TypedCommandWriter commandWriter;
  private final EventTriggerBehavior eventTriggerBehavior;

  public BpmnEventSubscriptionBehavior(
      final BpmnStateBehavior stateBehavior,
      final CatchEventBehavior catchEventBehavior,
      final EventTriggerBehavior eventTriggerBehavior,
      final StateWriter stateWriter,
      final TypedCommandWriter commandWriter,
      final SideEffects sideEffects,
      final MutableZeebeState zeebeState) {
    this.stateBehavior = stateBehavior;
    this.catchEventBehavior = catchEventBehavior;
    this.eventTriggerBehavior = eventTriggerBehavior;
    this.stateWriter = stateWriter;
    this.commandWriter = commandWriter;
    this.sideEffects = sideEffects;

    processState = zeebeState.getProcessState();
    eventScopeInstanceState = zeebeState.getEventScopeInstanceState();
    elementInstanceState = zeebeState.getElementInstanceState();
    keyGenerator = zeebeState.getKeyGenerator();
  }

  public <T extends ExecutableCatchEventSupplier> Either<Failure, Void> subscribeToEvents(
      final T element, final BpmnElementContext context) {

    try {
      catchEventBehavior.subscribeToEvents(context, element, sideEffects, commandWriter);
      return Either.right(null);

    } catch (final MessageCorrelationKeyException e) {
      return Either.left(
          new Failure(e.getMessage(), ErrorType.EXTRACT_VALUE_ERROR, e.getVariableScopeKey()));

    } catch (final EvaluationException e) {
      return Either.left(
          new Failure(
              e.getMessage(), ErrorType.EXTRACT_VALUE_ERROR, context.getElementInstanceKey()));
    } catch (final MessageNameException e) {
      return Either.left(e.getFailure());
    }
  }

  public void unsubscribeFromEvents(final BpmnElementContext context) {
    catchEventBehavior.unsubscribeFromEvents(context, commandWriter, sideEffects);
  }

  /**
   * Checks if the given element instance was triggered by an event. Should be used in combination
   * with {@link #activateTriggeredEvent(long, long, EventTrigger, BpmnElementContext)}.
   *
   * @param context the element instance to check
   * @return the event data if the element instance was triggered. Otherwise, it returns {@link
   *     Optional#empty()}.
   */
  public Optional<EventTrigger> findEventTrigger(final BpmnElementContext context) {
    final var elementInstanceKey = context.getElementInstanceKey();
    return Optional.ofNullable(eventScopeInstanceState.peekEventTrigger(elementInstanceKey));
  }

  /**
   * Activates the element that was triggered by an event and pass in the variables of the event.
   * Should be called after {@link #findEventTrigger(BpmnElementContext)}. Depending on the event
   * type we need to give different flow scope key, e. g. for EventSubProcess the container
   * triggers/activates the EventSubProcess, while for boundary events the attached element triggers
   * that event which is not the flow scope.
   *
   * @param eventScopeKey the event scope key of the triggered event, can be the same as the flow
   *     scope key depending of the type of event
   * @param flowScopeKey the flow scope of event element to activate, which can be different based
   *     on the event type
   * @param eventTrigger the event data returned by {@link #findEventTrigger(BpmnElementContext)}
   * @param context the current processing context
   */
  public void activateTriggeredEvent(
      final long eventScopeKey,
      final long flowScopeKey,
      final EventTrigger eventTrigger,
      final BpmnElementContext context) {

    final var triggeredEvent =
        processState.getFlowElement(
            context.getProcessDefinitionKey(),
            eventTrigger.getElementId(),
            ExecutableFlowElement.class);

    eventTriggerBehavior.activateTriggeredEvent(
        eventTrigger.getEventKey(),
        triggeredEvent,
        eventScopeKey,
        flowScopeKey,
        context.getRecordValue(),
        eventTrigger.getVariables());
  }

  public Optional<EventTrigger> getEventTriggerForProcessDefinition(
      final long processDefinitionKey) {
    final var eventTrigger = eventScopeInstanceState.peekEventTrigger(processDefinitionKey);
    return Optional.ofNullable(eventTrigger);
  }

  public void activateTriggeredStartEvent(
      final BpmnElementContext context, final EventTrigger triggeredEvent) {
    final long processDefinitionKey = context.getProcessDefinitionKey();
    final long processInstanceKey = context.getProcessInstanceKey();

    final var process = processState.getProcessByKey(context.getProcessDefinitionKey());
    if (process == null) {
      // this should never happen because processes are never deleted.
      throw new BpmnProcessingException(
          context, String.format(NO_PROCESS_FOUND_MESSAGE, processDefinitionKey));
    }

    eventTriggerBehavior.processEventTriggered(
        triggeredEvent.getEventKey(),
        processDefinitionKey,
        processInstanceKey,
        processDefinitionKey, /* the event scope for the start event is the process definition */
        triggeredEvent.getElementId());

    eventRecord.reset();
    eventRecord.wrap(context.getRecordValue());

    final var record =
        eventRecord
            .setElementId(triggeredEvent.getElementId())
            .setBpmnElementType(BpmnElementType.START_EVENT)
            .setProcessInstanceKey(processInstanceKey)
            .setVersion(process.getVersion())
            .setBpmnProcessId(process.getBpmnProcessId())
            .setFlowScopeKey(processInstanceKey);

    final var newEventInstanceKey = keyGenerator.nextKey();
    commandWriter.appendFollowUpCommand(
        newEventInstanceKey, ProcessInstanceIntent.ACTIVATE_ELEMENT, record);
  }
}
