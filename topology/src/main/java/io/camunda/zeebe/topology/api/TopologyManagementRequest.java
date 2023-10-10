/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology.api;

import io.atomix.cluster.MemberId;
import java.util.Set;

/** Defines the supported requests for the topology management. */
public sealed interface TopologyManagementRequest {
  record AddMembersRequest(Set<MemberId> members) implements TopologyManagementRequest {}

  record JoinPartitionRequest(MemberId memberId, int partitionId, int priority)
      implements TopologyManagementRequest {}

  record LeavePartitionRequest(MemberId memberId, int partitionId)
      implements TopologyManagementRequest {}

  record ReassignPartitionsRequest(Set<MemberId> members) implements TopologyManagementRequest {}
}
