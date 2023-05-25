/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.jobstream;

import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.protocol.impl.stream.job.ActivatedJob;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import org.slf4j.Logger;

public class YieldingJobStreamErrorHandler implements JobStreamErrorHandler {

  private static final Logger LOG = Loggers.JOB_STREAM;

  @Override
  public void handleError(
      final ActivatedJob job, final Throwable error, final TaskResultBuilder resultBuilder) {
    LOG.warn("Failed to push job {}. Yielding...", job.jobKey(), error);
    resultBuilder.appendCommandRecord(job.jobKey(), JobIntent.YIELD, job.jobRecord());
  }
}
