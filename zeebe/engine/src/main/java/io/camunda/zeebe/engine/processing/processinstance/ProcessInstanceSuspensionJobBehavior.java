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
import java.util.function.Predicate;
import org.jspecify.annotations.NullMarked;

/**
 * Parks and un-parks the jobs of a process instance while it is suspended, walking its
 * element-instance tree.
 *
 * <p>Jobs of called child instances are untouched — {@link ElementInstanceState#getChildren} never
 * returns a child instance's root, so the walk can't reach them; the {@code processInstanceKey}
 * filter below is a defensive backstop for that.
 */
@NullMarked
public final class ProcessInstanceSuspensionJobBehavior {

  /**
   * States {@link JobIntent#SUSPENDED} may leave from; includes secret-waiting so a later
   * resolution can't reactivate a job while its instance stays suspended.
   */
  private static final Set<State> SUSPENDABLE_STATES =
      EnumSet.of(State.ACTIVATABLE, State.WAITING_FOR_SECRET_RESOLUTION);

  /** The only state {@link JobIntent#RESUMED} leaves; see {@code JobResumedApplier}. */
  private static final Set<State> RESUMABLE_STATES = EnumSet.of(State.SUSPENDED);

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

  /** Appends {@link JobIntent#SUSPENDED} for every job in {@link #SUSPENDABLE_STATES}. */
  public void suspendJobs(final long processInstanceKey) {
    final var processInstance = elementInstanceState.getInstance(processInstanceKey);
    if (processInstance == null) {
      return;
    }
    walk(
        processInstance,
        // never stops early: every suspendable job must be parked
        elementInstance ->
            visitJobInStates(
                elementInstance,
                SUSPENDABLE_STATES,
                (jobKey, job) -> {
                  stateWriter.appendFollowUpEvent(jobKey, JobIntent.SUSPENDED, job);
                  return true;
                }));
  }

  /**
   * Visits {@link State#SUSPENDED} jobs of the process instance until {@code visitor} stops the
   * walk, without changing their state. Takes the already-loaded root so a per-cycle caller doesn't
   * re-read it.
   */
  public void forEachSuspendedJob(final ElementInstance processInstance, final JobVisitor visitor) {
    walk(
        processInstance,
        elementInstance -> visitJobInStates(elementInstance, RESUMABLE_STATES, visitor));
  }

  /**
   * Walks the element-instance tree breadth-first, calling {@code visitor} per element until it
   * returns {@code false}. A queue (not recursion) avoids blowing the stack on a deep instance.
   */
  private void walk(final ElementInstance root, final Predicate<ElementInstance> visitor) {
    final long processInstanceKey = root.getValue().getProcessInstanceKey();
    final var elementInstances = new ArrayDeque<ElementInstance>();
    elementInstances.add(root);
    while (!elementInstances.isEmpty()) {
      final var elementInstance = elementInstances.poll();
      if (!visitor.test(elementInstance)) {
        return;
      }
      // defensive invariant, see class javadoc: getChildren never returns a child instance's root
      elementInstanceState.getChildren(elementInstance.getKey()).stream()
          .filter(child -> child.getValue().getProcessInstanceKey() == processInstanceKey)
          .forEach(elementInstances::add);
    }
  }

  /**
   * Passes this element's job to {@code visitor} if it is in one of {@code states}; otherwise
   * continues the walk.
   */
  private boolean visitJobInStates(
      final ElementInstance elementInstance, final Set<State> states, final JobVisitor visitor) {
    final long jobKey = elementInstance.getJobKey();
    if (jobKey <= 0 || !states.contains(jobState.getState(jobKey))) {
      return true;
    }
    final var job = jobState.getJob(jobKey);
    return job == null || visitor.visit(jobKey, job);
  }

  /** Visits one job of the process instance and reports whether the walk continues. */
  @FunctionalInterface
  public interface JobVisitor {

    /**
     * @param job valid only for this call — the job state reuses one record instance on every read,
     *     so copy what you need rather than hold the reference.
     */
    boolean visit(long jobKey, JobRecord job);
  }
}
