/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.SuspensionAware;
import io.camunda.zeebe.engine.processing.streamprocessor.SuspensionAware.SuspensionBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessMessageSubscriptionState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.SuspensionState;
import io.camunda.zeebe.engine.state.immutable.SuspensionState.BufferedCommand;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.engine.state.message.ProcessMessageSubscription;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.BufferedCommandRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.BufferedCommandIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.ArrayDeque;
import java.util.function.Consumer;
import org.jspecify.annotations.NullMarked;

/**
 * Drains buffered commands one per {@code DRAIN} cycle, then re-checks the buffer — already
 * current, since event appliers run synchronously — to decide the next step: another {@code DRAIN}
 * if more remains, or {@code RESUME_JOBS} to hand off to {@link
 * ProcessInstanceResumeJobsProcessor}, which un-parks jobs and appends {@code COMPLETE_RESUMING}
 * itself. {@link SuspensionBehavior#PROCESS} is unconditional: gating a {@code DRAIN} would strand
 * the instance in {@code RESUMING} forever.
 *
 * <p>A cycle that fails to write (e.g. batch size exceeded) halts rather than drops the command:
 * the default error handling rejects {@code DRAIN} without banning the instance, and it stays
 * {@code RESUMING} until a fresh {@code RESUME} restarts the drain (see {@link
 * ProcessInstanceResumeProcessor}).
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

  public BufferedCommandDrainProcessor(
      final ProcessingState processingState, final Writers writers) {
    stateWriter = writers.state();
    commandWriter = writers.command();
    rejectionWriter = writers.rejection();
    suspensionState = processingState.getSuspensionState();
    elementInstanceState = processingState.getElementInstanceState();
    processMessageSubscriptionState = processingState.getProcessMessageSubscriptionState();
  }

  @Override
  public void processRecord(final TypedRecord<BufferedCommandRecord> command) {
    final var drainValue = command.getValue();
    final long processInstanceKey = drainValue.getProcessInstanceKey();

    final var buffered = suspensionState.getOldestBufferedCommand(processInstanceKey).orElse(null);
    if (buffered == null) {
      advanceOrWait(command, drainValue);
      return;
    }

    appendBufferedCommand(buffered);
    appendDrainedEvent(buffered);

    // DRAINED already applied (event appliers run synchronously), so this reflects the buffer as
    // it stands now, without an extra empty DRAIN cycle to find out
    if (suspensionState.getOldestBufferedCommand(processInstanceKey).isEmpty()) {
      advanceOrWait(command, drainValue);
    } else {
      appendNextDrainCommand(drainValue);
    }
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
    appendResumeJobs(drainValue);
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
  public SuspensionBehavior suspensionBehavior(final TypedRecord<BufferedCommandRecord> record) {
    // DRAIN is what ends the suspension: gating it would strand the instance in RESUMING
    return SuspensionBehavior.PROCESS;
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

  private void appendNextDrainCommand(final BufferedCommandRecord drainValue) {
    commandWriter.appendFollowUpCommand(
        drainValue.getProcessInstanceKey(),
        BufferedCommandIntent.DRAIN,
        new BufferedCommandRecord()
            .setProcessInstanceKey(drainValue.getProcessInstanceKey())
            .setProcessDefinitionKey(drainValue.getProcessDefinitionKey())
            .setTenantId(drainValue.getTenantId()));
  }

  private void appendResumeJobs(final BufferedCommandRecord drainValue) {
    commandWriter.appendFollowUpCommand(
        drainValue.getProcessInstanceKey(),
        ProcessInstanceIntent.RESUME_JOBS,
        new ProcessInstanceRecord()
            .setProcessInstanceKey(drainValue.getProcessInstanceKey())
            .setProcessDefinitionKey(drainValue.getProcessDefinitionKey())
            .setTenantId(drainValue.getTenantId()));
  }
}
