/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.clustermetadata.reconfig;

/**
 * A single safe Raft membership change on one topic partition: add ({@link Kind#JOIN}) or remove
 * ({@link Kind#LEAVE}) one replica. A reassignment is executed as a sequence of these, one at a
 * time per group, so the Raft group's membership only ever changes by a single member between
 * commits (Raft's safe-reconfiguration rule).
 *
 * @param topic the topic whose Raft group is reconfigured
 * @param partitionId the partition within that group (1-based)
 * @param member the broker node id joining or leaving
 */
public record ReconfigurationOp(Kind kind, String topic, int partitionId, int member) {

  public enum Kind {
    JOIN,
    LEAVE
  }
}
