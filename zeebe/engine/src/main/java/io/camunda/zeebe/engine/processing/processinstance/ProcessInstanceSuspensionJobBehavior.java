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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import org.jspecify.annotations.NullMarked;

/**
 * Suspends and resumes the jobs of a process instance while it is suspended.
 *
 * <p>{@link #suspendJobs} always walks the element-instance tree — suspending is what populates the
 * {@code JOBS_BY_PROCESS_INSTANCE} index, so the index can't help find newly-suspendable jobs.
 * {@link #forEachSuspendedJob} seeks that index from the resume cursor instead; keyed by each job's
 * own {@code processInstanceKey}, the index excludes a called child instance's jobs.
 *
 * <p>Jobs of called child instances are left untouched by the tree walk — {@link
 * ElementInstanceState#getChildren} never returns a child instance's root, so the walk can't reach
 * them; the {@code processInstanceKey} filter below is a defensive backstop for that.
 */
@NullMarked
public final class ProcessInstanceSuspensionJobBehavior {

  /**
   * States {@link JobIntent#SUSPENDED} may leave from; includes secret-waiting so a later
   * resolution can't reactivate a job while its instance stays suspended.
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
   * Appends {@link JobIntent#SUSPENDED} for every job in {@link #SUSPENDABLE_STATES}.
   *
   * @return the number of jobs suspended; the caller records the metric after all writes complete
   */
  public int suspendJobs(final long processInstanceKey) {
    final var processInstance = elementInstanceState.getInstance(processInstanceKey);
    if (processInstance == null) {
      return 0;
    }
    final var count = new AtomicInteger();
    walk(
        processInstance,
        elementInstance ->
            visitJobInStates(
                elementInstance,
                SUSPENDABLE_STATES,
                (jobKey, job) -> {
                  stateWriter.appendFollowUpEvent(jobKey, JobIntent.SUSPENDED, job);
                  count.incrementAndGet();
                  return true;
                }));
    return count.get();
  }

  /**
   * Visits {@link State#SUSPENDED} jobs of the process instance until {@code visitor} stops the
   * walk, without changing their state. Seeks the {@code JOBS_BY_PROCESS_INSTANCE} index from
   * {@code startAfterJobKey} (inclusive): the cursor marks the last job a previous cycle resumed,
   * so every entry from there onward is either already resumed (skipped) or halts the scan for this
   * cycle. Takes the already-loaded root so a per-cycle caller doesn't re-read it, even though only
   * its {@code processInstanceKey} is used here.
   *
   * <p>If the index entry for {@code startAfterJobKey} no longer exists (e.g. the job was deleted
   * after the cursor was recorded), RocksDB's seek lands on the next key ≥ {@code
   * startAfterJobKey}, so iteration naturally continues from there.
   */
  public void forEachSuspendedJob(
      final ElementInstance processInstance,
      final long startAfterJobKey,
      final JobVisitor visitor) {
    final long processInstanceKey = processInstance.getValue().getProcessInstanceKey();
    jobState.forEachJobsByProcessInstance(
        processInstanceKey,
        startAfterJobKey,
        jobKey -> {
          if (jobState.getState(jobKey) != State.SUSPENDED) {
            return true;
          }
          final var job = jobState.getJob(jobKey);
          return job == null || visitor.visit(jobKey, job);
        });
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
