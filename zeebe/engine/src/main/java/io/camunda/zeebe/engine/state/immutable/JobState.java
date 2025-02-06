/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.engine.processing.identity.AuthorizedTenants;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import org.agrona.DirectBuffer;

public interface JobState {

  /**
   * Loops over all timed-out job entries and applies the provided callback.
   *
   * @param executionTimestamp Timestamp against which it's determined whether the deadline has
   *     expired
   * @param startAt Index used to start the iteration at; looping starts at the beginning when
   *     startAt is {@code null}
   * @param callback A callback method to be applied to each job entry. It must return a boolean
   *     that when {@code true} allows the loop to continue, or when {@code false} stops iteration.
   * @return The last visited index where the iteration has stopped because the {@code callback}
   *     method returned false or {@code null} if it was not the case.
   */
  DeadlineIndex forEachTimedOutEntry(
      long executionTimestamp, final DeadlineIndex startAt, BiPredicate<Long, JobRecord> callback);

  boolean exists(long jobKey);

  State getState(long key);

  boolean isInState(long key, State state);

  void forEachActivatableJobs(
      DirectBuffer type,
      final List<String> tenantIds,
      BiFunction<Long, JobRecord, Boolean> callback);

  JobRecord getJob(long key);

  JobRecord getJob(final long key, final AuthorizedTenants authorizedTenantIds);

  boolean jobDeadlineExists(final long jobKey, final long deadline);

  long findBackedOffJobs(final long timestamp, final BiPredicate<Long, JobRecord> callback);

  /** Index to point to a specific position in the jobs with deadline column family. */
  record DeadlineIndex(long deadline, long key) {}

  enum State {
    ACTIVATABLE((byte) 0),
    ACTIVATED((byte) 1),
    FAILED((byte) 2),
    NOT_FOUND((byte) 3),
    ERROR_THROWN((byte) 4);

    byte value;

    State(final byte value) {
      this.value = value;
    }
  }
}
