/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.job;

import io.camunda.zeebe.gateway.impl.broker.request.BrokerActivateJobsRequest;
import io.camunda.zeebe.gateway.impl.job.JobActivationResult.ActivatedJob;
import io.camunda.zeebe.scheduler.ActorControl;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Can handle an 'activate jobs' request from a client. */
public interface ActivateJobsHandler<T> extends Consumer<ActorControl> {

  static final AtomicLong ACTIVATE_JOBS_REQUEST_ID_GENERATOR = new AtomicLong(1);

  /**
   * Handle activate jobs request from a client
   *
   * @param request The request to handle
   */
  void activateJobs(
      final BrokerActivateJobsRequest request,
      final ResponseObserver<T> responseObserver,
      final Consumer<Runnable> setCancelHandler,
      final long requestTimeout);

  /**
   * Reactivate jobs that were activated but could not be delivered to the client. This allows the
   * jobs to be picked up by other workers.
   *
   * @param jobs the jobs to yield
   * @param reason the reason for yielding
   */
  default void yieldJobs(final List<ActivatedJob> jobs, final String reason) {
    // no-op by default
  }
}
