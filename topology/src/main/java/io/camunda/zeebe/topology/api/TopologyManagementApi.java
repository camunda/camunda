/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology.api;

import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.topology.api.TopologyManagementRequest.AddMembersRequest;
import io.camunda.zeebe.topology.api.TopologyManagementRequest.JoinPartitionRequest;
import io.camunda.zeebe.topology.api.TopologyManagementRequest.LeavePartitionRequest;
import io.camunda.zeebe.topology.api.TopologyManagementRequest.ReassignPartitionsRequest;
import io.camunda.zeebe.topology.api.TopologyManagementRequest.RemoveMembersRequest;
import io.camunda.zeebe.topology.api.TopologyManagementResponse.TopologyChangeStatus;

/** Defines the API for the topology management requests. */
public interface TopologyManagementApi {

  ActorFuture<TopologyChangeStatus> addMembers(AddMembersRequest addMembersRequest);

  ActorFuture<TopologyChangeStatus> removeMembers(RemoveMembersRequest removeMembersRequest);

  ActorFuture<TopologyChangeStatus> joinPartition(JoinPartitionRequest joinPartitionRequest);

  ActorFuture<TopologyChangeStatus> leavePartition(LeavePartitionRequest leavePartitionRequest);

  ActorFuture<TopologyChangeStatus> reassignPartitions(
      ReassignPartitionsRequest reassignPartitionsRequest);
}
