/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.job;

import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.stream.api.ExternalJobActivator;
import io.prometheus.client.Histogram;
import java.util.Objects;
import java.util.Optional;
import org.agrona.DirectBuffer;

/**
 * A {@link ExternalJobActivator} which wraps another implementation, but will collect the job's
 * variables before sending it out.
 */
final class CollectingExternalJobActivator implements ExternalJobActivator {
  private static final Histogram JOB_COLLECTING_LATENCY =
      Histogram.build()
          .namespace("zeebe_job_stream")
          .name("collecting_latency")
          .help("Time to collect the variables for the job")
          .register();

  private final ExternalJobActivator delegate;
  private final JobCollector jobCollector;

  CollectingExternalJobActivator(
      final ExternalJobActivator delegate, final JobCollector jobCollector) {
    this.delegate = Objects.requireNonNull(delegate, "must delegate activation");
    this.jobCollector = Objects.requireNonNull(jobCollector, "must specify a job collector");
  }

  @Override
  public Optional<Handler> activateJob(final DirectBuffer type) {
    return delegate.activateJob(type).map(CollectingHandler::new);
  }

  private final class CollectingHandler implements Handler {
    private final Handler delegate;

    private CollectingHandler(final Handler delegate) {
      this.delegate = Objects.requireNonNull(delegate, "must delegate handling");
    }

    @Override
    public void handle(final long key, final JobRecord job) {
      try (final var timer = JOB_COLLECTING_LATENCY.startTimer()) {
        jobCollector.collectJob(job);
      }

      delegate.handle(key, job);
    }
  }
}
