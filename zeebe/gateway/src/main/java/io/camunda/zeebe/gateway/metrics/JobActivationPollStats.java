/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.metrics;

import java.util.Map;

/**
 * What it took to activate jobs for one poll, i.e. one attempt to activate jobs across the
 * partitions for a request.
 *
 * @param parallel whether partitions were asked in parallel, as opposed to one after the other
 * @param requests the number of requests sent to brokers
 * @param rounds the longest chain of requests that had to wait for one another; for sequential
 *     activation this is the number of requests
 * @param jobsPerPartition the number of jobs received from each partition that was asked
 * @param surplusJobs the number of jobs that were activated beyond what the request needed and were
 *     given back to the broker
 */
public record JobActivationPollStats(
    boolean parallel,
    int requests,
    int rounds,
    Map<Integer, Integer> jobsPerPartition,
    int surplusJobs) {

  public int jobs() {
    return jobsPerPartition.values().stream().mapToInt(Integer::intValue).sum();
  }
}
