/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.stream.api;

import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import java.util.Optional;
import org.agrona.DirectBuffer;

/**
 * Implementations of this interface help the engine decide whether to immediately activate a job
 * when it's made available. This is primarily used to immediately push jobs out to external workers
 * without going through a poll-request-cycle.
 */
@FunctionalInterface
public interface ExternalJobActivator {

  Optional<Handler> activateJob(final DirectBuffer type);

  @FunctionalInterface
  interface Handler {
    void handle(final long key, final JobRecord job);
  }
}
