/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology.serializer;

import io.camunda.zeebe.topology.api.TopologyChangeResponse;
import io.camunda.zeebe.topology.api.TopologyManagementRequest;

public interface TopologyRequestsSerializer {

  byte[] encodeAddMembersRequest(TopologyManagementRequest.AddMembersRequest req);

  byte[] encodeRemoveMembersRequest(TopologyManagementRequest.RemoveMembersRequest req);

  byte[] encodeJoinPartitionRequest(TopologyManagementRequest.JoinPartitionRequest req);

  byte[] encodeLeavePartitionRequest(TopologyManagementRequest.LeavePartitionRequest req);

  byte[] encodeReassignPartitionsRequest(
      TopologyManagementRequest.ReassignPartitionsRequest reassignPartitionsRequest);

  byte[] encodeScaleRequest(TopologyManagementRequest.ScaleRequest scaleRequest);

  TopologyManagementRequest.AddMembersRequest decodeAddMembersRequest(byte[] encodedState);

  TopologyManagementRequest.RemoveMembersRequest decodeRemoveMembersRequest(byte[] encodedState);

  TopologyManagementRequest.JoinPartitionRequest decodeJoinPartitionRequest(byte[] encodedState);

  TopologyManagementRequest.LeavePartitionRequest decodeLeavePartitionRequest(byte[] encodedState);

  TopologyManagementRequest.ReassignPartitionsRequest decodeReassignPartitionsRequest(
      byte[] encodedState);

  TopologyManagementRequest.ScaleRequest decodeScaleRequest(byte[] encodedState);

  byte[] encode(TopologyChangeResponse topologyChangeResponse);

  TopologyChangeResponse decodeTopologyChangeResponse(byte[] encodedTopologyChangeResponse);
}
