/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.immutable.JobState.State;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.BiConsumer;
import org.jspecify.annotations.NullMarked;

/**
 * Parks jobs of a process instance so they are no longer handed out while the instance is
 * suspended.
 *
 * <p>The element-instance walk never reaches a called child instance: {@link
 * ElementInstanceState#getChildren} is driven by an element instance's {@code parentKey}, and a
 * called child instance's root element has no such parent link (its {@code flowScopeKey} defaults
 * to {@code -1}, which resolves to no element instance). There is simply no tree edge from the call
 * activity to the child's root to walk across.
 *
 * <p>The boundary matters because a suspension marker is keyed by the suspended instance alone: a
 * called child instance's own commands are not gated by it, so if this behavior parked the child's
 * jobs too, the child would un-park them again on its own.
 *
 * <p>The {@code processInstanceKey} filter below is a defensive invariant check for that boundary,
 * not the mechanism that enforces it.
 */
@NullMarked
public final class ProcessInstanceSuspensionJobBehavior {

  /**
   * States that {@link JobIntent#SUSPENDED} may leave. Matches {@code JobSuspendedApplier}: secret
   * waiting is overridden so a later secret reactivation cannot put the job back into the hand-out
   * index while the process instance is still suspended.
   */
  private static final Set<State> SUSPENDABLE_STATES =
      EnumSet.of(State.ACTIVATABLE, State.WAITING_FOR_SECRET_RESOLUTION);

  private final ElementInstanceState elementInstanceState;
  private final JobState jobState;
  private final StateWriter stateWriter;

  public ProcessInstanceSuspensionJobBehavior(
      final ElementInstanceState elementInstanceState,
      final JobState jobState,
      final StateWriter stateWriter) {
    this.elementInstanceState = elementInstanceState;
    this.jobState = jobState;
    this.stateWriter = stateWriter;
  }

  /**
   * Appends {@link JobIntent#SUSPENDED} for every suspendable job of the process instance ({@link
   * State#ACTIVATABLE} and {@link State#WAITING_FOR_SECRET_RESOLUTION}), which parks each job out
   * of the hand-out index.
   */
  public void suspendJobs(final long processInstanceKey) {
    forEachJobInStates(
        processInstanceKey,
        SUSPENDABLE_STATES,
        (jobKey, job) -> stateWriter.appendFollowUpEvent(jobKey, JobIntent.SUSPENDED, job));
  }

  private void forEachJobInStates(
      final long processInstanceKey,
      final Set<State> states,
      final BiConsumer<Long, JobRecord> consumer) {
    final var processInstance = elementInstanceState.getInstance(processInstanceKey);
    if (processInstance == null) {
      return;
    }

    // a queue instead of recursion, to not blow the stack on a deeply nested instance
    final var elementInstances = new ArrayDeque<ElementInstance>();
    elementInstances.add(processInstance);
    while (!elementInstances.isEmpty()) {
      final var elementInstance = elementInstances.poll();
      visitJob(elementInstance, states, consumer);
      // defensive invariant, see class javadoc: getChildren never returns a child instance's root
      elementInstanceState.getChildren(elementInstance.getKey()).stream()
          .filter(child -> child.getValue().getProcessInstanceKey() == processInstanceKey)
          .forEach(elementInstances::add);
    }
  }

  private void visitJob(
      final ElementInstance elementInstance,
      final Set<State> states,
      final BiConsumer<Long, JobRecord> consumer) {
    final long jobKey = elementInstance.getJobKey();
    if (jobKey <= 0 || !states.contains(jobState.getState(jobKey))) {
      return;
    }
    final var job = jobState.getJob(jobKey);
    if (job != null) {
      consumer.accept(jobKey, job);
    }
  }
}
