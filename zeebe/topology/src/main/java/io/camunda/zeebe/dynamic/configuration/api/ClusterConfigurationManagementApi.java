/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.dynamic.configuration.api;

import io.camunda.zeebe.dynamic.configuration.api.ClusterConfigurationManagementRequest.AddMembersRequest;
import io.camunda.zeebe.dynamic.configuration.api.ClusterConfigurationManagementRequest.JoinPartitionRequest;
import io.camunda.zeebe.dynamic.configuration.api.ClusterConfigurationManagementRequest.LeavePartitionRequest;
import io.camunda.zeebe.dynamic.configuration.api.ClusterConfigurationManagementRequest.ReassignPartitionsRequest;
import io.camunda.zeebe.dynamic.configuration.api.ClusterConfigurationManagementRequest.RemoveMembersRequest;
import io.camunda.zeebe.dynamic.configuration.api.ClusterConfigurationManagementRequest.ScaleRequest;
import io.camunda.zeebe.dynamic.configuration.state.ClusterConfiguration;
import io.camunda.zeebe.scheduler.future.ActorFuture;

/** Defines the API for the topology management requests. */
public interface ClusterConfigurationManagementApi {

  ActorFuture<ClusterConfigurationChangeResponse> addMembers(AddMembersRequest addMembersRequest);

  ActorFuture<ClusterConfigurationChangeResponse> removeMembers(
      RemoveMembersRequest removeMembersRequest);

  ActorFuture<ClusterConfigurationChangeResponse> joinPartition(
      JoinPartitionRequest joinPartitionRequest);

  ActorFuture<ClusterConfigurationChangeResponse> leavePartition(
      LeavePartitionRequest leavePartitionRequest);

  ActorFuture<ClusterConfigurationChangeResponse> reassignPartitions(
      ReassignPartitionsRequest reassignPartitionsRequest);

  ActorFuture<ClusterConfigurationChangeResponse> scaleMembers(ScaleRequest scaleRequest);

  /**
   * Forces a scale down of the cluster. The members that are not specified in the request will be
   * removed forcefully. The replicas of partitions on the removed members won't be re-assigned. As
   * a result the number of replicas for those partitions will be reduced.
   *
   * <p>This is expected to be used to force remove a set of brokers when they are unreachable.
   */
  ActorFuture<ClusterConfigurationChangeResponse> forceScaleDown(
      ScaleRequest forceScaleDownRequest);

  ActorFuture<ClusterConfiguration> cancelTopologyChange(
      ClusterConfigurationManagementRequest.CancelChangeRequest cancelChangeRequest);

  ActorFuture<ClusterConfiguration> getTopology();
}
