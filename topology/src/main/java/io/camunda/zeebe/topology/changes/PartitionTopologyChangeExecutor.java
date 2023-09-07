/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology.changes;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import java.util.Map;

/**
 * Represents the executor that executes the actual process to start or start. The concrete
 * implementation of this interface is expected to be a call back to the system component that can
 * start or stop partition. This is typically the PartitionManager in the Broker.
 */
public interface PartitionTopologyChangeExecutor {

  // The implementation should be able to build new PartitionMetadata from the given partitionId and
  // priorities.
  ActorFuture<Void> join(int partitionId, Map<MemberId, Integer> membersWithPriority);

  ActorFuture<Void> leave(int partitionId);
}
