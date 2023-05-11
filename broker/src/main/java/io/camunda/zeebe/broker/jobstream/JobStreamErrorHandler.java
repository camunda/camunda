/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.jobstream;

import io.camunda.zeebe.protocol.impl.stream.job.ActivatedJob;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;

/**
 * The {@link JobStreamErrorHandler} allows specifying logic which should be executed whenever the
 * broker fails to push a job to one of the open streams.
 *
 * <p>Implementations can then produce followup records to be appended to the log in order to
 * perform any actions (e.g. yield the job).
 */
@FunctionalInterface
public interface JobStreamErrorHandler {

  /**
   * Called when a previously activated job, which was supposed to be pushed, cannot be pushed due
   * to some error.
   *
   * <p>Implementations should use the given {@link TaskResultBuilder} to append followup commands
   * to the same partition as the job's.
   *
   * @param job the activated job which should have been pushed
   * @param error the last error which caused the failure
   * @param resultBuilder the result builder on which you can add followup commands to be processed
   */
  void handleError(
      final ActivatedJob job, final Throwable error, final TaskResultBuilder resultBuilder);
}
