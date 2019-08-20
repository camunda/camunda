/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.clustering.base.partitions;

public interface PartitionRoleChangeListener {
  /* when this node becomes a follower for partition partitionId*/
  void onTransitionToFollower(int partitionId);

  /* when this node becomes the leader for partition partitionId in term leaderTerm */
  void onTransitionToLeader(int partitionId, long leaderTerm);
}
