/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.job;

import io.camunda.zeebe.gateway.metrics.JobActivationPollStats;
import io.camunda.zeebe.scheduler.ActorControl;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Activates jobs for one in-flight request across the partitions of a cluster. It is the part of
 * job activation that the long polling handler delegates to.
 */
interface PartitionedActivateJobsHandler<T> extends Consumer<ActorControl> {

  /**
   * Activates jobs until the request is satisfied or no partition can provide more.
   *
   * @param onError called at most once, when the request failed
   * @param onCompleted called once when done, with the number of jobs that could not be activated
   *     and whether any partition answered with RESOURCE_EXHAUSTED
   * @param onPollFinished called once before either of the above, with what the poll took
   */
  void activateJobs(
      int partitionsCount,
      InflightActivateJobsRequest<T> request,
      Consumer<Throwable> onError,
      BiConsumer<Integer, Boolean> onCompleted,
      Consumer<JobActivationPollStats> onPollFinished);

  /**
   * Tells the handler that a partition has jobs of the given type, so that it can ask that
   * partition first. Can be called from any thread.
   */
  default void hintJobsAvailable(
      final String partitionGroup, final String jobType, final int partitionId) {}
}
