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
 * <p>Jobs of called child instances are left untouched. {@link ElementInstanceState#getChildren}
 * does not return a child instance's root for the given element instance key, so the walk never
 * reaches those jobs. The {@code processInstanceKey} filter below is only a defensive check of that
 * boundary.
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
