/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import io.camunda.zeebe.engine.metrics.SuspensionMetrics;
import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.SuspensionAware;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.processing.usertask.UserTaskSuspensionBehavior;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessMessageSubscriptionState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.SuspensionState;
import io.camunda.zeebe.engine.state.immutable.SuspensionState.BufferedCommand;
import io.camunda.zeebe.engine.state.immutable.UserTaskState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.engine.state.message.ProcessMessageSubscription;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.BufferedCommandRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BufferedCommandIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.ArrayDeque;
import java.util.function.Consumer;
import org.jspecify.annotations.NullMarked;

/**
 * Drains buffered commands one per {@code DRAIN} cycle: if one is found, it's drained and another
 * {@code DRAIN} is scheduled unconditionally; the cycle that finds the buffer empty hands off to
 * {@code RESUME_JOBS} instead, to {@link ProcessInstanceResumeJobsProcessor}, which un-parks jobs
 * and appends {@code COMPLETE_RESUMING} itself. {@link SuspensionAction#PROCESS} is unconditional:
 * gating a {@code DRAIN} would strand the instance in {@code RESUMING} forever.
 *
 * <p>A cycle that fails to write (e.g. batch size exceeded) halts rather than drops the command:
 * the default error handling rejects {@code DRAIN} without banning the instance, and it stays
 * {@code RESUMING} until a fresh {@code RESUME} restarts the drain (see {@link
 * ProcessInstanceResumeProcessor}).
 *
 * <p>User task entries buffered on suspend and resume (see {@link UserTaskSuspensionBehavior}) are
 * not replayed as commands: the cycle writes their {@code SUSPENDED} or {@code RESUMED} event
 * directly. While the instance is {@code SUSPENDING}, the drain handles only these user task
 * entries, and hands off to {@code COMPLETE_SUSPENDING} once none are left. Other entries, such as
 * subscription {@code REOPEN}s buffered by close acknowledgements, stay in the buffer for the
 * resume drain.
 */
@ExcludeAuthorizationCheck
@NullMarked
public final class BufferedCommandDrainProcessor
    implements TypedRecordProcessor<BufferedCommandRecord>, SuspensionAware<BufferedCommandRecord> {

  private static final String WAITING_CLOSE_ACKS_MESSAGE =
      "Expected to finish draining process instance '%d', but some message subscriptions are still "
          + "closing — will retry once their delete acknowledgements arrive and buffer their reopen "
          + "commands.";

  private final StateWriter stateWriter;
  private final TypedCommandWriter commandWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final SuspensionState suspensionState;
  private final ElementInstanceState elementInstanceState;
  private final ProcessMessageSubscriptionState processMessageSubscriptionState;
  private final UserTaskState userTaskState;
  private final SuspensionMetrics suspensionMetrics;

  public BufferedCommandDrainProcessor(
      final ProcessingState processingState,
      final Writers writers,
      final SuspensionMetrics suspensionMetrics) {
    stateWriter = writers.state();
    commandWriter = writers.command();
    rejectionWriter = writers.rejection();
    suspensionState = processingState.getSuspensionState();
    elementInstanceState = processingState.getElementInstanceState();
    processMessageSubscriptionState = processingState.getProcessMessageSubscriptionState();
    userTaskState = processingState.getUserTaskState();
    this.suspensionMetrics = suspensionMetrics;
  }

  @Override
  public void processRecord(final TypedRecord<BufferedCommandRecord> command) {
    final var drainValue = command.getValue();
    final long processInstanceKey = drainValue.getProcessInstanceKey();

    if (suspensionState.getSuspensionState(processInstanceKey)
        == SuspensionState.State.SUSPENDING) {
      drainWhileSuspending(drainValue);
      return;
    }

    suspensionState
        .findNextBufferedCommand(processInstanceKey, drainValue.getCommandKey())
        .ifPresentOrElse(
            buffered -> {
              if (isUserTaskEvent(buffered.command())) {
                drainUserTaskEvent(buffered);
              } else {
                drainBufferedCommand(buffered);
              }
            },
            () -> advanceOrWait(command, drainValue));
  }

  /** Drains only user task entries; the others stay buffered for the resume drain. */
  private void drainWhileSuspending(final BufferedCommandRecord drainValue) {
    long afterCommandKey = drainValue.getCommandKey();
    var next =
        suspensionState.findNextBufferedCommand(
            drainValue.getProcessInstanceKey(), afterCommandKey);
    while (next.isPresent() && !isUserTaskEvent(next.get().command())) {
      afterCommandKey = next.get().key();
      next =
          suspensionState.findNextBufferedCommand(
              drainValue.getProcessInstanceKey(), afterCommandKey);
    }
    next.ifPresentOrElse(
        this::drainUserTaskEvent,
        () -> appendProcessInstanceCommand(drainValue, ProcessInstanceIntent.COMPLETE_SUSPENDING));
  }

  /** User task entries buffered on suspend or resume, see {@link UserTaskSuspensionBehavior}. */
  private static boolean isUserTaskEvent(final BufferedCommandRecord buffered) {
    return buffered.getValueType() == ValueType.USER_TASK
        && (buffered.getIntent() == UserTaskIntent.SUSPENDED
            || buffered.getIntent() == UserTaskIntent.RESUMED);
  }

  /** Writes the buffered user task event directly, instead of replaying a command. */
  private void drainUserTaskEvent(final BufferedCommand buffered) {
    final var value = buffered.command();
    final long userTaskKey = value.getCommandKey();
    final var userTask = userTaskState.getUserTask(userTaskKey);
    // the user task may have ended since it was buffered; skip it
    if (userTask != null && userTask.getProcessInstanceKey() == value.getProcessInstanceKey()) {
      stateWriter.appendFollowUpEvent(userTaskKey, value.getIntent(), userTask);
    }
    appendDrainedEvent(buffered);
    appendNextDrainCommand(buffered.key(), value);
  }

  private void drainBufferedCommand(final BufferedCommand buffered) {
    appendBufferedCommand(buffered);
    appendDrainedEvent(buffered);
    appendNextDrainCommand(buffered.key(), buffered.command());
    suspensionMetrics.commandDrained();
  }

  /**
   * Buffer drained. If a suspend-driven close is still CLOSING, its ack will restore the manifest
   * and buffer a {@code REOPEN}, so advancing to {@code RESUME_JOBS} now would finish the resume
   * before that row is re-subscribed; reject and wait (the delete-ack re-triggers a {@code DRAIN}).
   * {@code DRAIN} does not ban on error, so the rejection is a safe terminal record. Otherwise hand
   * off to {@code RESUME_JOBS}.
   *
   * <p>This resume-side wait exists only because suspend completes ({@code SUSPENDED}) while its
   * closes are still in flight, so resume can start with subscriptions still CLOSING. Once #61057
   * introduces a {@code SUSPENDING} state that finishes all closes before writing {@code
   * SUSPENDED}, {@code SUSPENDED} guarantees "all closed" and this gate (and {@link
   * #hasClosingSubscriptions}) can be removed.
   */
  private void advanceOrWait(
      final TypedRecord<BufferedCommandRecord> command, final BufferedCommandRecord drainValue) {
    if (hasClosingSubscriptions(drainValue.getProcessInstanceKey())) {
      rejectionWriter.appendRejection(
          command,
          RejectionType.INVALID_STATE,
          WAITING_CLOSE_ACKS_MESSAGE.formatted(drainValue.getProcessInstanceKey()));
      return;
    }
    appendProcessInstanceCommand(drainValue, ProcessInstanceIntent.RESUME_JOBS);
  }

  /** Read-only BFS over the element-instance tree: reports whether any subscription is CLOSING. */
  private boolean hasClosingSubscriptions(final long processInstanceKey) {
    final boolean[] hasClosing = {false};
    visitTreeSubscriptions(
        processInstanceKey,
        subscription -> {
          if (subscription.isClosing()) {
            hasClosing[0] = true;
          }
        });
    return hasClosing[0];
  }

  private void visitTreeSubscriptions(
      final long processInstanceKey, final Consumer<ProcessMessageSubscription> visitor) {
    final var root = elementInstanceState.getInstance(processInstanceKey);
    if (root == null) {
      return;
    }
    final var queue = new ArrayDeque<ElementInstance>();
    queue.add(root);
    while (!queue.isEmpty()) {
      final var elementInstance = queue.poll();
      processMessageSubscriptionState.visitElementSubscriptions(
          elementInstance.getKey(),
          subscription -> {
            visitor.accept(subscription);
            return true;
          });
      elementInstanceState.getChildren(elementInstance.getKey()).stream()
          .filter(child -> child.getValue().getProcessInstanceKey() == processInstanceKey)
          .forEach(queue::add);
    }
  }

  @Override
  public SuspensionAction onSuspended(final TypedRecord<BufferedCommandRecord> record) {
    // DRAIN is what ends the suspension: gating it would strand the instance in RESUMING
    return SuspensionAction.PROCESS;
  }

  @Override
  public SuspensionAction onResuming(final TypedRecord<BufferedCommandRecord> record) {
    return SuspensionAction.PROCESS;
  }

  private void appendBufferedCommand(final BufferedCommand buffered) {
    final var value = buffered.command();
    commandWriter.appendFollowUpCommand(
        value.getCommandKey(), value.getIntent(), value.getCommandValue());
  }

  // DRAINED carries no payload: the applier removes the entry by record key, so repeating it would
  // halve the maximum buffered command size for no gain.
  private void appendDrainedEvent(final BufferedCommand buffered) {
    final var value = buffered.command();
    stateWriter.appendFollowUpEvent(
        buffered.key(),
        BufferedCommandIntent.DRAINED,
        new BufferedCommandRecord()
            .setProcessInstanceKey(value.getProcessInstanceKey())
            .setProcessDefinitionKey(value.getProcessDefinitionKey())
            .setTenantId(value.getTenantId())
            .setCommandKey(value.getCommandKey())
            .setValueType(value.getValueType())
            .setIntent(value.getIntent()));
  }

  private void appendNextDrainCommand(
      final long drainedCommandKey, final BufferedCommandRecord drainedCommandValue) {
    commandWriter.appendFollowUpCommand(
        drainedCommandValue.getProcessInstanceKey(),
        BufferedCommandIntent.DRAIN,
        new BufferedCommandRecord()
            .setProcessInstanceKey(drainedCommandValue.getProcessInstanceKey())
            .setProcessDefinitionKey(drainedCommandValue.getProcessDefinitionKey())
            .setTenantId(drainedCommandValue.getTenantId())
            .setCommandKey(drainedCommandKey));
  }

  private void appendProcessInstanceCommand(
      final BufferedCommandRecord drainValue, final ProcessInstanceIntent intent) {
    commandWriter.appendFollowUpCommand(
        drainValue.getProcessInstanceKey(),
        intent,
        new ProcessInstanceRecord()
            .setProcessInstanceKey(drainValue.getProcessInstanceKey())
            .setProcessDefinitionKey(drainValue.getProcessDefinitionKey())
            .setTenantId(drainValue.getTenantId()));
  }
}
