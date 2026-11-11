/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions.leadership;

import io.atomix.raft.partition.RaftPartition;

/**
 * Hands out the leadership of each partition of a broker. It is the one choice between the modes:
 * leaders from Raft, or leaders from leases in the database.
 */
public interface PartitionLeaderships extends AutoCloseable {

  PartitionLeadership forPartition(RaftPartition raftPartition);

  /** True if the leaders come from leases in the database. */
  boolean isLeaseMode();

  @Override
  void close();

  /** The leadership that the Raft election decides, as without a lease store. */
  static PartitionLeaderships raft() {
    return new PartitionLeaderships() {
      @Override
      public PartitionLeadership forPartition(final RaftPartition raftPartition) {
        return new RaftPartitionLeadership(raftPartition);
      }

      @Override
      public boolean isLeaseMode() {
        return false;
      }

      @Override
      public void close() {}
    };
  }
}
