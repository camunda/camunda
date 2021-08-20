/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.util;

import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.client.api.worker.JobHandler;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RecordingJobHandler implements JobHandler {
  protected final JobHandler[] jobHandlers;
  protected final List<ActivatedJob> handledJobs = Collections.synchronizedList(new ArrayList<>());
  protected int nextJobHandler = 0;

  public RecordingJobHandler() {
    this(
        (controller, job) -> {
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

  public void clear() {
    handledJobs.clear();
  }
}
