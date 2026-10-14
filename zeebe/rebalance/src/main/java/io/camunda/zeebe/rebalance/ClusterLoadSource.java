/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.rebalance;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import java.time.Duration;
import java.util.Collection;

/** Measures how busy the cluster is over a window starting now. */
@FunctionalInterface
public interface ClusterLoadSource {
  /**
   * @param members the brokers expected to report, including this one if it is a member
   */
  ActorFuture<ClusterLoad> collect(Collection<MemberId> members, Duration window);
}
