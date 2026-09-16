/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.secretreference;

import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.intent.JobIntent;

/**
 * Appends the {@link JobIntent#SECRET_RESOLUTION_PARKED} / {@link
 * JobIntent#SECRET_RESOLUTION_RESUMED} markers that make the secret park/un-park transition
 * observable on the JOB record stream for the wait-state exporter. The transition itself is owned
 * by the {@code SecretReferenceIntent} appliers; these events carry no state of their own.
 */
public final class SecretResolutionJobEvents {

  private SecretResolutionJobEvents() {}

  /**
   * Appends {@code intent} for {@code job} on the JOB record stream, but only if the current record
   * batch can still fit the event, sized the same way the batch collector sizes the job records it
   * hands out.
   *
   * <p>Returns whether the event was appended. A {@code false} result means the batch is full: the
   * job's parked/reactivated state already lives in the job state regardless, so the only
   * consequence is that its wait-state mark is corrected a cycle later or on completion. Callers
   * emitting for several jobs in a loop should stop on {@code false}, since a full batch does not
   * regain room within the same cycle.
   */
  public static boolean appendIfBatchHasRoom(
      final StateWriter stateWriter,
      final long jobKey,
      final JobIntent intent,
      final JobRecord job) {
    if (!stateWriter.canWriteEventOfLength(
        job.getLength() + EngineConfiguration.BATCH_SIZE_CALCULATION_BUFFER)) {
      return false;
    }
    stateWriter.appendFollowUpEvent(jobKey, intent, job);
    return true;
  }
}
