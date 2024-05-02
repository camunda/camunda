/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.topology.api;

import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.topology.api.TopologyManagementRequest.AddMembersRequest;
import io.camunda.zeebe.topology.api.TopologyManagementRequest.JoinPartitionRequest;
import io.camunda.zeebe.topology.api.TopologyManagementRequest.LeavePartitionRequest;
import io.camunda.zeebe.topology.api.TopologyManagementRequest.ReassignPartitionsRequest;
import io.camunda.zeebe.topology.api.TopologyManagementRequest.RemoveMembersRequest;
import io.camunda.zeebe.topology.api.TopologyManagementRequest.ScaleRequest;
import io.camunda.zeebe.topology.state.ClusterTopology;

/** Defines the API for the topology management requests. */
public interface TopologyManagementApi {

  ActorFuture<TopologyChangeResponse> addMembers(AddMembersRequest addMembersRequest);

  ActorFuture<TopologyChangeResponse> removeMembers(RemoveMembersRequest removeMembersRequest);

  ActorFuture<TopologyChangeResponse> joinPartition(JoinPartitionRequest joinPartitionRequest);

  ActorFuture<TopologyChangeResponse> leavePartition(LeavePartitionRequest leavePartitionRequest);

  ActorFuture<TopologyChangeResponse> reassignPartitions(
      ReassignPartitionsRequest reassignPartitionsRequest);

  ActorFuture<TopologyChangeResponse> scaleMembers(ScaleRequest scaleRequest);

  /**
   * Forces a scale down of the cluster. The members that are not specified in the request will be
   * removed forcefully. The replicas of partitions on the removed members won't be re-assigned. As
   * a result the number of replicas for those partitions will be reduced.
   *
   * <p>This is expected to be used to force remove a set of brokers when they are unreachable.
   */
  ActorFuture<TopologyChangeResponse> forceScaleDown(ScaleRequest forceScaleDownRequest);

  ActorFuture<ClusterTopology> cancelTopologyChange(
      TopologyManagementRequest.CancelChangeRequest cancelChangeRequest);

  ActorFuture<ClusterTopology> getTopology();
}
