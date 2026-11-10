/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.mailbox.api;

/**
 * The node that leads a partition, as the leader recorded it in the store. A gateway that does not
 * know the leader from the cluster topology uses it to see that a leader exists in another region.
 *
 * @param partitionId the partition
 * @param nodeId the node that leads it, not blank
 * @param zone the zone (region) of the node, not blank
 * @param term the term of the leader. A record with a higher term replaces one with a lower term,
 *     never the other way round.
 */
public record PartitionLeader(int partitionId, String nodeId, String zone, long term) {

  public PartitionLeader {
    if (nodeId.isBlank()) {
      throw new IllegalArgumentException("node id must not be blank");
    }
    if (zone.isBlank()) {
      throw new IllegalArgumentException("zone must not be blank");
    }
  }
}
