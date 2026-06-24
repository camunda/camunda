/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.state.group;

import java.util.List;
import java.util.Map;

/**
 * An immutable snapshot of a consumer group's replicated membership and target assignment,
 * maintained by the appliers as a thread-safe mirror of {@link DbConsumerGroupState}. It is read
 * off the stream-processing actor — by the async assignor task (to compute a rebalance) and by the
 * coordinator's heartbeat handler (to serve assign/revoke deltas) — without touching RocksDB.
 *
 * @param groupId the group id
 * @param groupEpoch the desired-state version (bumped on every membership change)
 * @param assignmentEpoch the group epoch the member targets reflect ({@code < groupEpoch} ⇒
 *     rebalance pending)
 * @param partitionCount the topic's partition count
 * @param members the current roster, {@code memberId → snapshot}
 */
public record GroupSnapshot(
    String groupId,
    long groupEpoch,
    long assignmentEpoch,
    int partitionCount,
    Map<String, MemberSnapshot> members) {

  public boolean isRebalancePending() {
    return assignmentEpoch < groupEpoch;
  }

  /** One member's replicated identity, epoch, and target partitions. */
  public record MemberSnapshot(
      String memberId, String instanceId, long memberEpoch, List<Integer> targetPartitions) {}
}
