/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.timer;

import io.camunda.security.core.auth.RequiredAuthorization;
import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.common.CatchEventBehavior;
import io.camunda.zeebe.engine.processing.common.EventHandle;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableCatchEvent;
import io.camunda.zeebe.engine.processing.identity.AuthorizationRejectionMapper;
import io.camunda.zeebe.engine.processing.identity.authorization.CslAuthorizationCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.SuspensionAware;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.immutable.TimerInstanceState;
import io.camunda.zeebe.engine.state.instance.TimerInstance;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.model.bpmn.util.time.Interval;
import io.camunda.zeebe.model.bpmn.util.time.RepeatingInterval;
import io.camunda.zeebe.model.bpmn.util.time.Timer;
import io.camunda.zeebe.protocol.impl.record.value.timer.TimerRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.mapper.AuthzModelMapper;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.Instant;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

@ExcludeAuthorizationCheck
public final class TimerTriggerProcessor
    implements TypedRecordProcessor<TimerRecord>, SuspensionAware<TimerRecord> {

  private static final String NO_TIMER_FOUND_MESSAGE =
      "Expected to trigger timer with key '%d', but no such timer was found";
  private static final String NO_HELD_TIMER_MESSAGE =
      "Expected to trigger a held timer of process instance '%d' at element '%s', but no such held"
          + " timer was found";
  private static final String AMBIGUOUS_HELD_TIMER_MESSAGE =
      "Expected to trigger a single held timer of process instance '%d' at element '%s', but"
          + " multiple held timers matched";
  private static final String NO_PROCESS_DEFINITION_FOUND_MESSAGE =
      "Expected to find a process definition with key '%d', but no such definition was found";
  private static final String NO_ACTIVE_TIMER_MESSAGE =
      "Expected to trigger a timer with key '%d', but the timer is not active anymore";
  private static final DirectBuffer NO_VARIABLES = new UnsafeBuffer();

  private final CatchEventBehavior catchEventBehavior;
  private final ProcessState processState;
  private final ElementInstanceState elementInstanceState;
  private final TimerInstanceState timerInstanceState;
  private final ExpressionProcessor expressionProcessor;
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final CslAuthorizationCheck cslCheck;

  private final EventHandle eventHandle;

  public TimerTriggerProcessor(
      final MutableProcessingState processingState,
      final BpmnBehaviors bpmnBehaviors,
      final Writers writers,
      final CslAuthorizationCheck cslCheck) {
    catchEventBehavior = bpmnBehaviors.catchEventBehavior();
    expressionProcessor = bpmnBehaviors.expressionProcessor();
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    this.cslCheck = cslCheck;

    processState = processingState.getProcessState();
    elementInstanceState = processingState.getElementInstanceState();
    timerInstanceState = processingState.getTimerState();
    keyGenerator = processingState.getKeyGenerator();
    eventHandle =
        new EventHandle(
            keyGenerator,
            processingState.getEventScopeInstanceState(),
            writers,
            processState,
            bpmnBehaviors.eventTriggerBehavior(),
            bpmnBehaviors.stateBehavior());
  }

  @Override
  public void processRecord(final TypedRecord<TimerRecord> record) {
    final var timer = record.getValue();
    // A client-issued trigger command (public REST endpoint) addresses a held timer by
    // (processInstanceKey, elementId); a scheduler-issued command carries the full value keyed by
    // the timer key. For the former we resolve the held timer, hydrate the command value from it,
    // authorize the caller, and use the resolved timer key for the remainder of processing.
    final long timerKey;
    if (record.hasRequestMetadata()) {
      final var resolvedTimerKey = resolveAndAuthorizeExternalTrigger(record);
      if (resolvedTimerKey == null) {
        return;
      }
      timerKey = resolvedTimerKey;
    } else {
      timerKey = record.getKey();
    }

    final var elementInstanceKey = timer.getElementInstanceKey();
    final var processDefinitionKey = timer.getProcessDefinitionKey();
    final var timerInstance = timerInstanceState.get(elementInstanceKey, timerKey);
    if (timerInstance == null) {
      reject(record, RejectionType.NOT_FOUND, NO_TIMER_FOUND_MESSAGE.formatted(timerKey));
      return;
    }

    final var tenantId = timer.getTenantId();
    // this is an additional safeguard to avoid banning unrelated instances
    // as noticed in https://github.com/camunda/camunda/issues/20677
    final var deployedProcess =
        processState.getProcessByKeyAndTenant(processDefinitionKey, tenantId);
    if (deployedProcess == null) {
      reject(
          record,
          RejectionType.NOT_FOUND,
          NO_PROCESS_DEFINITION_FOUND_MESSAGE.formatted(processDefinitionKey));
      return;
    }

    final var catchEvent =
        processState.getFlowElement(
            processDefinitionKey,
            tenantId,
            timer.getTargetElementIdBuffer(),
            ExecutableCatchEvent.class);
    if (isStartEvent(elementInstanceKey)) {
      if (deployedProcess.isDraining()) {
        // The definition is draining: never spawn a new instance. Still mark the timer as
        // triggered so the command is consumed
        stateWriter.appendFollowUpEvent(timerKey, TimerIntent.TRIGGERED, timer);
        // skip rescheduling since instances can't be created anymore
        return;
      }
      final long processInstanceKey = keyGenerator.nextKey();
      timer.setProcessInstanceKey(processInstanceKey);
      stateWriter.appendFollowUpEvent(timerKey, TimerIntent.TRIGGERED, timer);
      eventHandle.activateProcessInstanceForStartEvent(
          processDefinitionKey,
          processInstanceKey,
          timer.getTargetElementIdBuffer(),
          NO_VARIABLES,
          tenantId);
    } else {
      final var elementInstance = elementInstanceState.getInstance(elementInstanceKey);
      if (!eventHandle.canTriggerElement(elementInstance, timer.getTargetElementIdBuffer())) {
        rejectNoActiveTimer(record, timerKey);
        return;
      }

      stateWriter.appendFollowUpEvent(timerKey, TimerIntent.TRIGGERED, timer);
      eventHandle.activateElement(catchEvent, elementInstanceKey, elementInstance.getValue());
    }

    if (record.hasRequestMetadata()) {
      responseWriter.writeAcceptedResponseOnCommand(timerKey, TimerIntent.TRIGGERED, timer, record);
    }

    if (shouldReschedule(timer)) {
      rescheduleTimer(timer, catchEvent);
    }
  }

  /**
   * Resolves the held timer addressed by a client-issued trigger command via (processInstanceKey,
   * elementId) and authorizes the caller. Returns {@code null} (after writing a rejection) when no
   * held timer matches, when more than one matches, or when the caller is not permitted to update
   * the owning process instance. On success the command value is hydrated from the stored timer so
   * the remainder of {@link #processRecord} behaves identically to a scheduler-issued trigger, and
   * the resolved timer key is returned.
   */
  private Long resolveAndAuthorizeExternalTrigger(final TypedRecord<TimerRecord> record) {
    final var command = record.getValue();
    final var processInstanceKey = command.getProcessInstanceKey();
    final var elementId = command.getTargetElementIdBuffer();
    final var elementIdString = BufferUtil.bufferAsString(elementId);

    final var resolution =
        timerInstanceState.resolveHeldByProcessElement(processInstanceKey, elementId);
    switch (resolution.status()) {
      case NOT_FOUND -> {
        reject(
            record,
            RejectionType.NOT_FOUND,
            NO_HELD_TIMER_MESSAGE.formatted(processInstanceKey, elementIdString));
        return null;
      }
      case AMBIGUOUS -> {
        reject(
            record,
            RejectionType.INVALID_STATE,
            AMBIGUOUS_HELD_TIMER_MESSAGE.formatted(processInstanceKey, elementIdString));
        return null;
      }
      default -> {
        // FOUND: fall through to authorization
      }
    }

    final var held = resolution.timer();
    final long timerKey = held.getKey();
    // hydrate before authorization: reading the shared timer instance now avoids any later state
    // access mutating it, and lets the authorization check use the command's own bpmnProcessId.
    hydrateFromStoredTimer(command, held);

    final var isAuthorized =
        cslCheck.checkAuthorizationAndTenant(
            record,
            RequiredAuthorization.of(
                b ->
                    b.resourceType(
                            AuthzModelMapper.fromProtocol(
                                AuthorizationResourceType.PROCESS_DEFINITION))
                        .permissionType(
                            AuthzModelMapper.fromProtocol(PermissionType.UPDATE_PROCESS_INSTANCE))
                        .resourceId(command.getBpmnProcessId())),
            command,
            AuthorizationRejectionMapper.forbidden(
                PermissionType.UPDATE_PROCESS_INSTANCE,
                AuthorizationResourceType.PROCESS_DEFINITION),
            command.getTenantId(),
            // NOT_FOUND, not FORBIDDEN: a caller outside the owning tenant must not be able to tell
            // a held timer it may not touch from one that does not exist.
            new Rejection(
                RejectionType.NOT_FOUND,
                NO_HELD_TIMER_MESSAGE.formatted(processInstanceKey, elementIdString)));
    if (isAuthorized.isLeft()) {
      final var rejection = isAuthorized.getLeft();
      reject(record, rejection.type(), rejection.reason());
      return null;
    }

    return timerKey;
  }

  private void hydrateFromStoredTimer(final TimerRecord timer, final TimerInstance held) {
    timer
        .setElementInstanceKey(held.getElementInstanceKey())
        .setProcessInstanceKey(held.getProcessInstanceKey())
        .setProcessDefinitionKey(held.getProcessDefinitionKey())
        .setTargetElementId(held.getHandlerNodeId())
        .setRepetitions(held.getRepetitions())
        .setDueDate(held.getDueDate())
        .setTenantId(held.getTenantId())
        .setRootProcessInstanceKey(held.getRootProcessInstanceKey())
        .setBpmnProcessId(held.getBpmnProcessId())
        .setElementType(held.getElementType())
        .setHeld(held.isHeld());
  }

  private void reject(
      final TypedRecord<TimerRecord> record, final RejectionType type, final String reason) {
    rejectionWriter.appendRejection(record, type, reason);
    if (record.hasRequestMetadata()) {
      responseWriter.writeRejectedResponseOnCommand(record, type, reason);
    }
  }

  private void rejectNoActiveTimer(final TypedRecord<TimerRecord> record, final long timerKey) {
    reject(record, RejectionType.INVALID_STATE, NO_ACTIVE_TIMER_MESSAGE.formatted(timerKey));
  }

  private boolean isStartEvent(final long elementInstanceKey) {
    return elementInstanceKey < 0;
  }

  private boolean shouldReschedule(final TimerRecord timer) {
    return timer.getRepetitions() == RepeatingInterval.INFINITE || timer.getRepetitions() > 1;
  }

  private void rescheduleTimer(final TimerRecord record, final ExecutableCatchEvent event) {
    final Either<Failure, Timer> timer =
        event
            .getTimerFactory()
            .apply(expressionProcessor, record.getElementInstanceKey(), record.getTenantId());
    if (timer.isLeft()) {
      final String message =
          "Expected to reschedule repeating timer for element with id '%s', but an error occurred: %s"
              .formatted(BufferUtil.bufferAsString(event.getId()), timer.getLeft().getMessage());
      throw new IllegalStateException(message);
      // todo(#4208): raise incident instead of throwing an exception
    }

    final Timer refreshedTimer = refreshTimer(timer.get(), record);
    catchEventBehavior.subscribeToTimerEvent(
        record.getElementInstanceKey(),
        record.getProcessInstanceKey(),
        record.getProcessDefinitionKey(),
        event.getId(),
        record.getTenantId(),
        record.getRootProcessInstanceKey(),
        record.getBpmnProcessId(),
        record.getElementType(),
        refreshedTimer);
  }

  private Timer refreshTimer(final Timer timer, final TimerRecord record) {
    if (timer instanceof CronTimer) {
      return timer;
    }

    int repetitions = record.getRepetitions();
    if (repetitions != RepeatingInterval.INFINITE) {
      repetitions--;
    }

    // Use the timer's last due date instead of the current time to avoid a time shift.
    final Interval refreshedInterval =
        timer.getInterval().withStart(Instant.ofEpochMilli(record.getDueDate()));
    return new RepeatingInterval(repetitions, refreshedInterval);
  }

  @Override
  public SuspensionAction onSuspended(final TypedRecord<TimerRecord> record) {
    if (!record.isInternalCommand()) {
      // A client-issued trigger carries no timer key and no due date to index, so there is nothing
      // to suspend; buffering it would leave the caller's request unanswered until resume.
      return SuspensionAction.REJECT;
    }
    stateWriter.appendFollowUpEvent(record.getKey(), TimerIntent.SUSPENDED, record.getValue());
    return SuspensionAction.BUFFER;
  }

  @Override
  public SuspensionAction onResuming(final TypedRecord<TimerRecord> record) {
    if (!record.isInternalCommand()) {
      return SuspensionAction.REJECT;
    }
    final long timerKey = record.getKey();
    final var timer = record.getValue();
    // RESUMED restores a missing due-date entry before the drained TRIGGER removes the timer.
    stateWriter.appendFollowUpEvent(timerKey, TimerIntent.RESUMED, timer);
    return SuspensionAction.PROCESS;
  }
}
