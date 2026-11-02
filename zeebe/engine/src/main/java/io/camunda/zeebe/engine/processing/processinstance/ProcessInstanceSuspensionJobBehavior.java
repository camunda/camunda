/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.immutable.JobState.State;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import org.jspecify.annotations.NullMarked;

/**
 * Finds the jobs of a suspended process instance and tells whether a job is exempt from suspension.
 * {@link JobSuspensionVisitor} suspends the jobs during the element instance walk.
 *
 * <p>{@link #forEachSuspendedJob} seeks the {@code JOBS_BY_PROCESS_INSTANCE} index from the resume
 * cursor; keyed by each job's own {@code processInstanceKey}, the index excludes a called child
 * instance's jobs.
 */
@NullMarked
public final class ProcessInstanceSuspensionJobBehavior {

  private final ElementInstanceState elementInstanceState;
  private final JobState jobState;

  public ProcessInstanceSuspensionJobBehavior(
      final ElementInstanceState elementInstanceState, final JobState jobState) {
    this.elementInstanceState = elementInstanceState;
    this.jobState = jobState;
  }

  /**
   * Whether the job stays available while its process instance is suspended. Jobs of a terminating
   * element are exempt, because the termination waits for them, e.g. a canceling task listener job.
   */
  public boolean isExemptFromSuspension(final JobRecord job) {
    final var elementInstance = elementInstanceState.getInstance(job.getElementInstanceKey());
    return elementInstance != null && elementInstance.isTerminating();
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
