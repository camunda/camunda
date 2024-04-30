/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import io.camunda.zeebe.protocol.impl.stream.job.ActivatedJob;
import io.camunda.zeebe.protocol.impl.stream.job.JobActivationProperties;
import java.util.Optional;
import java.util.function.Predicate;
import org.agrona.DirectBuffer;

/**
 * A {@link JobStreamer} allows the engine to push data back to a single gateway (any). It keeps
 * track of multiple {@link JobStream} instances, each with their own jobType.
 *
 * <p>NOTE: {@link JobStream#push(ActivatedJob)} is a side effect, and should be treated as a
 * post-commit task for consistency. TODO: see if the platform cannot already enforce with its own
 * implementation.
 *
 * <p>NOTE: implementations of the {@link JobStream#push(ActivatedJob)} method are likely
 * asynchronous.
 */
@FunctionalInterface
public interface JobStreamer {
  static JobStreamer noop() {
    return (jobType, filter) -> Optional.empty();
  }

  /**
   * Can be used to notify listeners that there are jobs available for activation.
   *
   * @param jobType the type of the stream which has items available
   */
  default void notifyWorkAvailable(final String jobType) {}

  /**
   * Returns a job stream for the job type, or {@link Optional#empty()} if there is none.
   *
   * <p>The predicate should return false to exclude job streams from the list of possible streams.
   *
   * @param jobType the job type to look for
   * @param filter a filter to include/exclude eligible job streams based on their properties
   * @return a job stream which matches the type and given filter, or {@link Optional#empty()} if
   *     none match
   */
  Optional<JobStream> streamFor(
      final DirectBuffer jobType, final Predicate<JobActivationProperties> filter);

  /** Returns a job stream for the job type, or {@link Optional#empty()} if there is none. */
  default Optional<JobStream> streamFor(final DirectBuffer jobType) {
    return streamFor(jobType, ignored -> true);
  }

  /** A {@link JobStream} allows consumers to push out activated jobs. */
  interface JobStream {

    /** Returns the properties used during job activation, e.g. timeout, worker */
    JobActivationProperties properties();

    /**
     * Pushes the given payload to the stream. Implementations of this are likely asynchronous; it's
     * recommended that callers ensure that the given payload is immutable, and that the error
     * handler does not close over any shared state.
     *
     * @param payload the data to push to the remote gateway
     */
    void push(final ActivatedJob payload);
  }
}
