/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.util;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import io.camunda.client.api.worker.JobHandler;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class RecordingJobHandler implements JobHandler {
  protected final JobHandler[] jobHandlers;
  protected final List<ActivatedJob> handledJobs = new CopyOnWriteArrayList<>();
  protected int nextJobHandler = 0;

  public RecordingJobHandler() {
    this(
        (client, job) -> {
          // do nothing
        });
  }

  public RecordingJobHandler(final JobHandler... jobHandlers) {
    this.jobHandlers = jobHandlers;
  }

  @Override
  public void handle(final JobClient client, final ActivatedJob job) throws Exception {
    final JobHandler handler = jobHandlers[nextJobHandler];
    nextJobHandler = Math.min(nextJobHandler + 1, jobHandlers.length - 1);

    try {
      handler.handle(client, job);
    } finally {
      handledJobs.add(job);
    }
  }

  public List<ActivatedJob> getHandledJobs() {
    return handledJobs;
  }

  public ActivatedJob getHandledJob(final String jobType) {
    return handledJobs.stream()
        .filter(j -> j.getType().equals(jobType))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("No job found with type " + jobType));
  }

  public void clear() {
    handledJobs.clear();
  }
}
