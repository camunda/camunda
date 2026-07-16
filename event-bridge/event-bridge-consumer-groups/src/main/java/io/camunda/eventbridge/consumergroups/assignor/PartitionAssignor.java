/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.assignor;

import io.camunda.eventbridge.protocol.topic.TopicPartition;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface PartitionAssignor {

  PartitionAssignment assign(final PartitionAssignmentContext context);

  /**
   * @param consumers the group's current member ids
   * @param assignment the previous target (active + standby), for stickiness
   * @param partitions every (topic, partition) across the group's subscriptions
   * @param standbyReplicas standby replicas per partition to place (consumer-groups ADR 0006
   *     decision 1); {@code 0} = no standbys, today's behavior
   * @param readyStandbys {@code memberId -> the partitions that member currently reports as a ready
   *     (caught-up) standby for} — used for ready-only promotion: a partition whose previous active
   *     owner left the group is only reassigned to a member found here, never to an arbitrary
   *     least-loaded member. Empty when readiness isn't tracked (no standbys), which preserves the
   *     original always-fill behavior exactly.
   */
  record PartitionAssignmentContext(
      List<String> consumers,
      PartitionAssignment assignment,
      List<TopicPartition> partitions,
      int standbyReplicas,
      Map<String, Set<TopicPartition>> readyStandbys) {

    /** Convenience constructor for callers/tests with no standby replicas. */
    public PartitionAssignmentContext(
        final List<String> consumers,
        final PartitionAssignment assignment,
        final List<TopicPartition> partitions) {
      this(consumers, assignment, partitions, 0, Map.of());
    }
  }
}
