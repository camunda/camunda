/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.state.immutable;

import io.zeebe.protocol.impl.record.value.job.JobRecord;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;

public interface JobState {

  void forEachTimedOutEntry(long upperBound, BiFunction<Long, JobRecord, Boolean> callback);

  boolean exists(long jobKey);

  State getState(long key);

  boolean isInState(long key, State state);

  void forEachActivatableJobs(DirectBuffer type, BiFunction<Long, JobRecord, Boolean> callback);

  JobRecord getJob(long key);

  void setJobsAvailableCallback(Consumer<String> callback);

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
