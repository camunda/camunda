/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import io.camunda.zeebe.engine.Loggers;
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
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ElementInstanceWalkRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ElementInstanceWalkIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.util.List;
import org.agrona.collections.MutableLong;
import org.slf4j.Logger;

/**
 * Walks the element instance tree of a suspending process instance depth-first (post-order), one
 * element instance per command, and writes {@link ProcessInstanceIntent#SUSPENDED} once the walk is
 * done.
 *
 * <p>Each command carries a cursor: {@code elementInstanceKey} is the element instance to suspend,
 * {@code parentKey} is its parent (or {@code -1} for the process instance), and {@code index} is
 * the last of its children that was already suspended ({@code -1} to start at the first child). If
 * the element instance still has unvisited children, the command only descends into the next one —
 * it writes a follow-up command and no event, the same way {@code
 * ProcessInstanceBatchTerminateProcessor} descends. Once an element instance has no more children
 * left to visit, it is suspended (visitors run, {@link
 * ElementInstanceWalkIntent#ELEMENT_INSTANCE_SUSPENDED} is appended) and the walk moves straight on
 * to its next sibling under the same parent; only once there is no sibling left does it ascend to
 * the parent, with {@code index} set to the element instance just suspended. The walk completes
 * with {@link ProcessInstanceIntent#SUSPENDED} once the root itself is suspended, since it has no
 * parent to continue to. Called process instances are not children of their call activity in the
 * element instance tree, so the walk does not enter them.
 *
 * <p>Each visited element instance is passed to every {@link ElementInstanceVisitor}, in order. The
 * order matters, since commands buffered for resume drain in the order they were buffered.
 *
 * <p>Each command writes exactly one follow-up command and runs in its own batch (see {@link
 * #shouldProcessResultsInSeparateBatches}), so the walk needs no recursion and its batch size does
 * not grow with the tree. Running each step in its own batch also lets the partition process other
 * commands in between walk steps, so suspending a large instance doesn't hurt processing
 * performance much.
 */
@ExcludeAuthorizationCheck
public final class ElementInstanceWalkSuspendElementInstanceProcessor
    implements TypedRecordProcessor<ElementInstanceWalkRecord>,
        SuspensionAware<ElementInstanceWalkRecord> {

  private static final Logger LOG = Loggers.PROCESS_PROCESSOR_LOGGER;

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
  private final ElementInstanceState elementInstanceState;
  private final SuspensionState suspensionState;
  private final SuspensionMetrics suspensionMetrics;
  private final List<ElementInstanceVisitor> visitors;

  public ElementInstanceWalkSuspendElementInstanceProcessor(
      final Writers writers,
      final KeyGenerator keyGenerator,
      final ElementInstanceState elementInstanceState,
      final SuspensionState suspensionState,
      final UserTaskState userTaskState,
      final SuspensionMetrics suspensionMetrics) {
    stateWriter = writers.state();
    commandWriter = writers.command();
    rejectionWriter = writers.rejection();
    this.elementInstanceState = elementInstanceState;
    this.suspensionState = suspensionState;
    this.suspensionMetrics = suspensionMetrics;
    final var bufferingBehavior =
        new CommandBufferingBehavior(keyGenerator, writers, suspensionMetrics);
    visitors =
        List.of(new UserTaskSuspensionVisitor(stateWriter, bufferingBehavior, userTaskState));
  }

  @Override
  public void processRecord(final TypedRecord<ElementInstanceWalkRecord> command) {
    final var value = command.getValue();
    final long processInstanceKey = value.getProcessInstanceKey();
    if (!validateProcessInstance(command, processInstanceKey)) {
      return;
    }

    final long instanceToSuspendKey = value.getElementInstanceKey();
    final var instanceToSuspend = elementInstanceState.getInstance(instanceToSuspendKey);
    if (instanceToSuspend == null) {
      // The instance to suspend was terminated mid-walk (instanceToSuspendKey == the process
      // instance is impossible here, validate already rejected that case). Continue at the next
      // sibling under its declared parent, ascend if there is none, or restart from the root if
      // the parent is gone too.
      continueWithNextSiblingOrParent(command, value.getParentKey(), instanceToSuspendKey);
      return;
    }

    final long childKey = findNextChildAfter(instanceToSuspend, value.getIndex());
    if (childKey != NO_KEY) {
      continueWithChild(command, childKey, instanceToSuspendKey);
    } else {
      suspend(command, instanceToSuspendKey, instanceToSuspend);
    }
  }

  /**
   * Without this, follow-up commands keep processing inline in the same batch, up to {@code
   * maxCommandsInBatch}. Each walk step can write sizeable records — {@code UserTask.SUSPENDED}
   * carries the full user task record, and the buffered {@code RESUME} command embeds it again — so
   * a wide tree would push the batch past the max record batch size, throwing {@code
   * ExceededBatchRecordSizeException} and forcing a rollback and retry one command at a time.
   * Separate batches also let other partition work interleave with a long walk.
   */
  @Override
  public boolean shouldProcessResultsInSeparateBatches() {
    return true;
  }

  @Override
  public SuspensionAction onSuspended(final TypedRecord<ElementInstanceWalkRecord> record) {
    return SuspensionAction.PROCESS;
  }

  @Override
  public SuspensionAction onResuming(final TypedRecord<ElementInstanceWalkRecord> record) {
    return SuspensionAction.PROCESS;
  }

  private boolean validateProcessInstance(
      final TypedRecord<ElementInstanceWalkRecord> command, final long processInstanceKey) {
    final var processInstance = elementInstanceState.getInstance(processInstanceKey);
    if (processInstance == null) {
      reject(command, INSTANCE_GONE_MESSAGE.formatted(processInstanceKey));
      return false;
    }

    final var processInstanceSuspensionState =
        suspensionState.getSuspensionState(processInstanceKey);
    if (processInstanceSuspensionState != SuspensionState.State.SUSPENDING) {
      reject(
          command,
          NOT_SUSPENDING_MESSAGE.formatted(processInstanceKey, processInstanceSuspensionState));
      return false;
    }

    if (processInstance.isTerminating()) {
      reject(command, TERMINATING_MESSAGE.formatted(processInstanceKey));
      return false;
    }
    return true;
  }

  /** No (more) children left to visit: suspend the instance itself and continue the walk. */
  private void suspend(
      final TypedRecord<ElementInstanceWalkRecord> command,
      final long instanceToSuspendKey,
      final ElementInstance instanceToSuspend) {
    final var value = command.getValue();
    visitors.stream()
        .filter(v -> v.shouldVisit(instanceToSuspend))
        .forEach(v -> v.visit(instanceToSuspend));
    stateWriter.appendFollowUpEvent(
        command.getKey(), ElementInstanceWalkIntent.ELEMENT_INSTANCE_SUSPENDED, value);

    if (isProcessInstance(instanceToSuspendKey, value)) {
      stateWriter.appendFollowUpEvent(
          value.getProcessInstanceKey(),
          ProcessInstanceIntent.SUSPENDED,
          instanceToSuspend.getValue());
      suspensionMetrics.instanceSuspended();
    } else {
      continueWithNextSiblingOrParent(
          command, instanceToSuspend.getParentKey(), instanceToSuspendKey);
    }
  }

  private boolean isProcessInstance(
      final long elementInstanceKey, final ElementInstanceWalkRecord value) {
    return elementInstanceKey == value.getProcessInstanceKey();
  }

  /** Descends into {@code childKey}, which starts at its own first child. */
  private void continueWithChild(
      final TypedRecord<ElementInstanceWalkRecord> command,
      final long childKey,
      final long parentKey) {
    appendContinueTraversal(command, childKey, parentKey, NO_KEY);
  }

  /**
   * Ascends to {@code parent}, with {@code index} set to {@code suspendedChildKey} so the parent's
   * own next step seeks from there.
   */
  private void continueWithParent(
      final TypedRecord<ElementInstanceWalkRecord> command,
      final ElementInstance parent,
      final long suspendedChildKey) {
    appendContinueTraversal(command, parent.getKey(), parent.getParentKey(), suspendedChildKey);
  }

  /**
   * Continues the walk after {@code suspendedChildKey} (now suspended) under {@code parentKey}:
   * moves on to the next sibling after {@code suspendedChildKey} if one exists; otherwise ascends
   * to {@code parentKey}. If {@code parentKey} itself no longer exists — e.g. concurrently
   * terminated between the child being found and continuing past it — restarts the walk from the
   * root so no remaining element instance is missed.
   */
  private void continueWithNextSiblingOrParent(
      final TypedRecord<ElementInstanceWalkRecord> command,
      final long parentKey,
      final long suspendedChildKey) {
    final var parent = parentKey > 0 ? elementInstanceState.getInstance(parentKey) : null;
    if (parent == null) {
      restartFromRoot(command, parentKey, suspendedChildKey);
      return;
    }

    final long siblingKey = findNextChildAfter(parent, suspendedChildKey);
    if (siblingKey != NO_KEY) {
      continueWithChild(command, siblingKey, parentKey);
    } else {
      continueWithParent(command, parent, suspendedChildKey);
    }
  }

  private void restartFromRoot(
      final TypedRecord<ElementInstanceWalkRecord> command,
      final long parentKey,
      final long suspendedChildKey) {
    final long processInstanceKey = command.getValue().getProcessInstanceKey();
    LOG.warn(
        "Expected to continue suspending process instance '{}' after element instance '{}', but "
            + "its parent element instance '{}' no longer exists. Restarting the suspension walk "
            + "from the process instance, which may emit duplicate user task events.",
        processInstanceKey,
        suspendedChildKey,
        parentKey);
    appendContinueTraversal(command, processInstanceKey, NO_KEY, NO_KEY);
  }

  /**
   * Returns the key of the first child of {@code parent} after {@code afterKey}, or {@code -1} if
   * there is none. Starts at the first child when {@code afterKey} is {@code -1}.
   */
  private long findNextChildAfter(final ElementInstance parent, final long afterKey) {
    if (parent.getNumberOfActiveElementInstances() <= 0) {
      return NO_KEY;
    }

    final MutableLong foundChildKey = new MutableLong(NO_KEY);
    elementInstanceState.forEachChild(
        parent.getKey(),
        afterKey + 1,
        (childKey, child) -> {
          foundChildKey.set(childKey);
          return false;
        });
    return foundChildKey.get();
  }

  private void appendContinueTraversal(
      final TypedRecord<ElementInstanceWalkRecord> command,
      final long elementInstanceKey,
      final long parentKey,
      final long index) {
    final var value = command.getValue();
    final var record =
        new ElementInstanceWalkRecord()
            .setProcessInstanceKey(value.getProcessInstanceKey())
            .setProcessDefinitionKey(value.getProcessDefinitionKey())
            .setStorageOrdinal(value.getStorageOrdinal())
            .setElementInstanceKey(elementInstanceKey)
            .setParentKey(parentKey)
            .setIndex(index);
    commandWriter.appendFollowUpCommand(
        command.getKey(), ElementInstanceWalkIntent.SUSPEND_ELEMENT_INSTANCE, record);
  }

  private void reject(final TypedRecord<ElementInstanceWalkRecord> command, final String reason) {
    rejectionWriter.appendRejection(command, RejectionType.INVALID_STATE, reason);
  }
}
