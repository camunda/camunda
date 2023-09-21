/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.util;

import io.camunda.zeebe.engine.processing.streamprocessor.JobStreamer;
import io.camunda.zeebe.protocol.impl.stream.job.ActivatedJob;
import io.camunda.zeebe.protocol.impl.stream.job.JobActivationProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import org.agrona.DirectBuffer;

public class RecordingJobStreamer implements JobStreamer {

  private final ConcurrentMap<String, AtomicInteger> jobNotifications = new ConcurrentHashMap<>();
  private final ConcurrentMap<DirectBuffer, RecordingJobStream> jobStreams =
      new ConcurrentHashMap<>();

  @Override
  public void notifyWorkAvailable(final String jobType) {
    final AtomicInteger counter = jobNotifications.getOrDefault(jobType, new AtomicInteger(0));
    counter.getAndIncrement();
  }

  @Override
  public Optional<JobStream> streamFor(
      final DirectBuffer jobType, final Predicate<JobActivationProperties> ignored) {
    // TODO: filter by activation properties using the predicate :)
    return Optional.ofNullable(jobStreams.get(jobType));
  }

  public RecordingJobStream addJobStream(
      final DirectBuffer jobType, final JobActivationProperties jobActivationProperties) {
    final var jobStream = new RecordingJobStream(jobActivationProperties);
    jobStreams.put(jobType, jobStream);
    return jobStream;
  }

  public static class RecordingJobStream implements JobStream {

    private final JobActivationProperties properties;
    private final List<ActivatedJob> activatedJobs;

    public RecordingJobStream(final JobActivationProperties properties) {
      this.properties = properties;
      activatedJobs = new ArrayList<>();
    }

    @Override
    public JobActivationProperties properties() {
      return properties;
    }

    @Override
    public void push(final ActivatedJob payload) {
      activatedJobs.add(payload);
    }

    public JobActivationProperties getProperties() {
      return properties;
    }

    public List<ActivatedJob> getActivatedJobs() {
      return activatedJobs;
    }

    public void clearActivatedJobs() {
      activatedJobs.clear();
    }
  }
}
