/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import io.camunda.zeebe.engine.metrics.SuspensionMetrics;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.immutable.JobState.State;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import java.util.EnumSet;
import java.util.Set;

/**
 * Writes {@link JobIntent#SUSPENDED} for the element instance's job, so the job leaves the
 * activatable index. {@link ProcessInstanceResumeJobsProcessor} resumes it again.
 *
 * <p>Only jobs in {@link #SUSPENDABLE_STATES} are suspended, so overlapping or restarted walks
 * suspend each job once. Jobs of terminating element instances are skipped: the termination waits
 * for them, e.g. a canceling task listener job.
 */
final class JobSuspensionVisitor implements ElementInstanceVisitor {

  /**
   * States {@link JobIntent#SUSPENDED} may leave from; includes secret-waiting so a later
   * resolution can't reactivate a job while its instance stays suspended.
   */
  private static final Set<State> SUSPENDABLE_STATES =
      EnumSet.of(State.ACTIVATABLE, State.WAITING_FOR_SECRET_RESOLUTION);

  private final StateWriter stateWriter;
  private final JobState jobState;
  private final SuspensionMetrics suspensionMetrics;

  JobSuspensionVisitor(
      final StateWriter stateWriter,
      final JobState jobState,
      final SuspensionMetrics suspensionMetrics) {
    this.stateWriter = stateWriter;
    this.jobState = jobState;
    this.suspensionMetrics = suspensionMetrics;
  }

  @Override
  public boolean shouldVisit(final ElementInstance elementInstance) {
    final long jobKey = elementInstance.getJobKey();
    return jobKey > 0
        && !elementInstance.isTerminating()
        && SUSPENDABLE_STATES.contains(jobState.getState(jobKey));
  }

  @Override
  public void visit(final ElementInstance elementInstance) {
    final long jobKey = elementInstance.getJobKey();
    final var job = jobState.getJob(jobKey);
    if (job == null) {
      return;
    }
    stateWriter.appendFollowUpEvent(jobKey, JobIntent.SUSPENDED, job);
    suspensionMetrics.jobSuspended();
  }
}
