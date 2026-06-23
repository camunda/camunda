/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.coordinator.reconfig;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Executes a single {@link ReconfigurationOp} against the cluster — i.e. tells the affected broker
 * to join or leave a topic partition's Raft group, and reports when the change is confirmed (the
 * Raft membership change has committed and, for a join, the new replica has caught up). The
 * returned future completing is the coordinator's signal to advance committed state by one step;
 * completing exceptionally (or timing out) means the step is retried.
 *
 * <p>The change-coordinator owns ordering, persistence and retry; this is just the "do one step and
 * confirm" boundary. In CC-2 it is a no-op stub that confirms immediately; CC-3 implements it on
 * the broker via runtime {@code RaftPartition.join()/leave()}.
 */
@FunctionalInterface
public interface ReconfigurationExecutor {

  /**
   * @param op the membership change to apply
   * @param partitionMembers the partition's replica node ids after this op (for a join, the set the
   *     joining broker must configure its Raft partition with)
   * @param partitionCount the topic's partition count (the joining broker needs it to set up the
   *     topic's routing topology)
   */
  CompletableFuture<Void> execute(
      ReconfigurationOp op, List<Integer> partitionMembers, int partitionCount);
}
