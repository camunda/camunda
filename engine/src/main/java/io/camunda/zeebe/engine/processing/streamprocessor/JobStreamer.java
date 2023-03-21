/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import java.util.Optional;
import org.agrona.DirectBuffer;

/**
 * A {@link JobStreamer} allows the engine to push data back to a single gateway (any). It keeps
 * track of multiple {@link JobStream} instances, each with their own jobType.
 *
 * <p>NOTE: {@link JobStream#push(ActivatedJob, ErrorHandler)} is a side effect, and should be
 * treated as a post-commit task for consistency. TODO: see if the platform cannot already enforce
 * with its own implementation.
 *
 * <p>NOTE: implementations of the {@link JobStream#push(ActivatedJob, ErrorHandler)} method are
 * likely asynchronous. As such, errors handled via the {@link ErrorHandler} may be executed after
 * the initial call. Callers should be careful with the state they close on in the implementations
 * of their {@link ErrorHandler}.
 */
@FunctionalInterface
public interface JobStreamer {
  static JobStreamer noop() {
    return jobType -> Optional.empty();
  }

  /**
   * Can be used to notify listeners that there are jobs available for activation.
   *
   * @param jobType the type of the stream which has items available
   */
  default void notifyWorkAvailable(final String jobType) {}

  /** Returns a job stream for the job type, or {@link Optional#empty()} if there is none. */
  Optional<JobStream> streamFor(final DirectBuffer jobType);

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
     * @param errorHandler logic to execute if the data could not be pushed to the underlying stream
     */
    void push(final ActivatedJob payload, ErrorHandler errorHandler);
  }

  /** Logic which is executed when the job cannot be pushed downstream. */
  interface ErrorHandler {
    void handleError(final Throwable error, ActivatedJob job);
  }
}
