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
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.SuspensionState;
import io.camunda.zeebe.engine.state.immutable.UserTaskState;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.SuspensionBatchRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.SuspensionBatchIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.util.List;

/**
 * Walks the element instance tree of a suspending process instance depth-first, one element
 * instance per command, and writes {@link ProcessInstanceIntent#SUSPENDED} once the walk is done.
 *
 * <p>Each command writes exactly one follow-up command and runs in its own batch (see {@link
 * #shouldProcessResultsInSeparateBatches}), so the walk needs no recursion and its batch size does
 * not grow with the tree. Called process instances are not children of their call activity in the
 * element instance tree, so the walk does not enter them.
 *
 * <p>Each visited element instance is passed to every {@link ElementInstanceSuspensionVisitor}, in
 * order. The order matters, since commands buffered for resume drain in the order they were
 * buffered.
 *
 * <p>Cursor of {@link SuspensionBatchIntent#SUSPEND_ELEMENT_INSTANCE}: {@code indexKey} is the
 * element instance to visit and {@code parentKey} is its parent, or {@code -1} for the process
 * instance. Cursor of {@link SuspensionBatchIntent#COMPLETE_SUSPENDING_ELEMENT_INSTANCE}: {@code
 * indexKey} is the element instance whose subtree was fully visited and {@code parentKey} is its
 * parent, or {@code -1}.
 */
@ExcludeAuthorizationCheck
public final class SuspensionBatchProcessor
    implements TypedRecordProcessor<SuspensionBatchRecord>, SuspensionAware<SuspensionBatchRecord> {

  private static final long NO_KEY = -1L;

  private static final String INSTANCE_GONE_MESSAGE =
      "Expected to continue suspending process instance '%d', but it no longer exists — likely "
          + "cancelled while suspending.";
  private static final String NOT_SUSPENDING_MESSAGE =
      "Expected to continue suspending process instance '%d', but its suspension marker is %s "
          + "instead of SUSPENDING.";
  private static final String TERMINATING_MESSAGE =
      "Expected to continue suspending process instance '%d', but it is being terminated.";

  private final StateWriter stateWriter;
  private final TypedCommandWriter commandWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final KeyGenerator keyGenerator;
  private final ElementInstanceState elementInstanceState;
  private final SuspensionState suspensionState;
  private final SuspensionMetrics suspensionMetrics;
  private final List<ElementInstanceSuspensionVisitor> visitors;

  private long foundChildKey;
  private int bufferedCommands;

  public SuspensionBatchProcessor(
      final Writers writers,
      final KeyGenerator keyGenerator,
      final ElementInstanceState elementInstanceState,
      final SuspensionState suspensionState,
      final UserTaskState userTaskState,
      final SuspensionMetrics suspensionMetrics) {
    stateWriter = writers.state();
    commandWriter = writers.command();
    rejectionWriter = writers.rejection();
    this.keyGenerator = keyGenerator;
    this.elementInstanceState = elementInstanceState;
    this.suspensionState = suspensionState;
    this.suspensionMetrics = suspensionMetrics;
    final var bufferingBehavior =
        new CommandBufferingBehavior(keyGenerator, writers, suspensionMetrics);
    visitors =
        List.of(new UserTaskSuspensionVisitor(stateWriter, bufferingBehavior, userTaskState));
  }

  @Override
  public void processRecord(final TypedRecord<SuspensionBatchRecord> command) {
    final var value = command.getValue();
    final long processInstanceKey = value.getProcessInstanceKey();
    if (!validate(command, processInstanceKey)) {
      return;
    }

    stateWriter.appendFollowUpEvent(
        command.getKey(), SuspensionBatchIntent.ELEMENT_INSTANCE_SUSPENDED, value);

    bufferedCommands = 0;
    final boolean suspended =
        switch ((SuspensionBatchIntent) command.getIntent()) {
          case SUSPEND_ELEMENT_INSTANCE -> suspendElementInstance(value);
          case COMPLETE_SUSPENDING_ELEMENT_INSTANCE ->
              continueAfter(value, value.getIndexKey(), value.getParentKey());
          default ->
              throw new IllegalStateException(
                  "Unexpected suspension batch intent " + command.getIntent());
        };

    for (int i = 0; i < bufferedCommands; i++) {
      suspensionMetrics.commandBuffered();
    }
    if (suspended) {
      suspensionMetrics.instanceSuspended();
    }
  }

  /**
   * Without this, the stream processor runs the follow-up commands in the same batch, up to its
   * command limit, so a wide tree would put many visits into one log batch.
   */
  @Override
  public boolean shouldProcessResultsInSeparateBatches() {
    return true;
  }

  @Override
  public SuspensionAction onSuspended(final TypedRecord<SuspensionBatchRecord> record) {
    return SuspensionAction.PROCESS;
  }

  @Override
  public SuspensionAction onResuming(final TypedRecord<SuspensionBatchRecord> record) {
    return SuspensionAction.PROCESS;
  }

  private boolean validate(
      final TypedRecord<SuspensionBatchRecord> command, final long processInstanceKey) {
    final var processInstance = elementInstanceState.getInstance(processInstanceKey);
    if (processInstance == null) {
      reject(command, INSTANCE_GONE_MESSAGE.formatted(processInstanceKey));
      return false;
    }

    final var marker = suspensionState.getSuspensionState(processInstanceKey);
    if (marker != SuspensionState.State.SUSPENDING) {
      reject(command, NOT_SUSPENDING_MESSAGE.formatted(processInstanceKey, marker));
      return false;
    }

    if (processInstance.isTerminating()) {
      reject(command, TERMINATING_MESSAGE.formatted(processInstanceKey));
      return false;
    }
    return true;
  }

  /** Visits the element instance, then descends into its first child or moves on. */
  private boolean suspendElementInstance(final SuspensionBatchRecord value) {
    final long elementInstanceKey = value.getIndexKey();
    visit(elementInstanceKey);
    final long firstChildKey = findChild(elementInstanceKey, NO_KEY);
    if (firstChildKey != NO_KEY) {
      appendSuspendElementInstance(value, firstChildKey, elementInstanceKey);
      return false;
    }
    return continueAfter(value, elementInstanceKey, value.getParentKey());
  }

  private void visit(final long elementInstanceKey) {
    final var elementInstance = elementInstanceState.getInstance(elementInstanceKey);
    if (elementInstance == null) {
      return;
    }
    for (final var visitor : visitors) {
      bufferedCommands += visitor.visit(elementInstance);
    }
  }

  /**
   * Continues the walk after the subtree of {@code elementInstanceKey} was visited: moves to the
   * next sibling, completes the parent, or completes the suspension once the root is done.
   *
   * @return {@code true} if the process instance is now suspended
   */
  private boolean continueAfter(
      final SuspensionBatchRecord value, final long elementInstanceKey, final long parentKey) {
    final long processInstanceKey = value.getProcessInstanceKey();
    if (parentKey == NO_KEY) {
      final var processInstance = elementInstanceState.getInstance(processInstanceKey);
      stateWriter.appendFollowUpEvent(
          processInstanceKey, ProcessInstanceIntent.SUSPENDED, processInstance.getValue());
      return true;
    }

    final long siblingKey = findChild(parentKey, elementInstanceKey);
    if (siblingKey != NO_KEY) {
      appendSuspendElementInstance(value, siblingKey, parentKey);
      return false;
    }

    if (parentKey == processInstanceKey) {
      appendCompleteSuspendingElementInstance(value, parentKey, NO_KEY);
      return false;
    }

    final var parent = elementInstanceState.getInstance(parentKey);
    if (parent == null) {
      // The parent finished an earlier termination after the walk entered it, so its position in
      // the tree is lost. Walk again from the root so no remaining element instance is missed.
      appendSuspendElementInstance(value, processInstanceKey, NO_KEY);
      return false;
    }
    appendCompleteSuspendingElementInstance(value, parentKey, parent.getParentKey());
    return false;
  }

  /**
   * Returns the key of the first child of {@code parentKey} after {@code afterKey}, or {@code -1}
   * if there is none. Starts at the first child when {@code afterKey} is {@code -1}.
   */
  private long findChild(final long parentKey, final long afterKey) {
    foundChildKey = NO_KEY;
    elementInstanceState.forEachChild(
        parentKey,
        afterKey,
        (childKey, child) -> {
          if (childKey == afterKey) {
            return true;
          }
          foundChildKey = childKey;
          return false;
        });
    return foundChildKey;
  }

  private void appendSuspendElementInstance(
      final SuspensionBatchRecord value, final long indexKey, final long parentKey) {
    appendFollowUpCommand(
        SuspensionBatchIntent.SUSPEND_ELEMENT_INSTANCE, value, indexKey, parentKey);
  }

  private void appendCompleteSuspendingElementInstance(
      final SuspensionBatchRecord value, final long indexKey, final long parentKey) {
    appendFollowUpCommand(
        SuspensionBatchIntent.COMPLETE_SUSPENDING_ELEMENT_INSTANCE, value, indexKey, parentKey);
  }

  private void appendFollowUpCommand(
      final SuspensionBatchIntent intent,
      final SuspensionBatchRecord value,
      final long indexKey,
      final long parentKey) {
    final var followUp =
        new SuspensionBatchRecord()
            .setProcessInstanceKey(value.getProcessInstanceKey())
            .setProcessDefinitionKey(value.getProcessDefinitionKey())
            .setStorageOrdinal(value.getStorageOrdinal())
            .setIndexKey(indexKey)
            .setParentKey(parentKey);
    commandWriter.appendFollowUpCommand(keyGenerator.nextKey(), intent, followUp);
  }

  private void reject(final TypedRecord<SuspensionBatchRecord> command, final String reason) {
    rejectionWriter.appendRejection(command, RejectionType.INVALID_STATE, reason);
  }
}
