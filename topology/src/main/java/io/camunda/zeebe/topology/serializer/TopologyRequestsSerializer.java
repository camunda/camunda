/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology.serializer;

import io.camunda.zeebe.topology.api.TopologyManagementRequest;
import io.camunda.zeebe.topology.api.TopologyManagementRequest.AddMembersRequest;
import io.camunda.zeebe.topology.api.TopologyManagementResponse.TopologyChangeStatus;

public interface TopologyRequestsSerializer {
  byte[] encode(AddMembersRequest addMembersRequest);

  TopologyManagementRequest.AddMembersRequest decodeAddMembersRequest(byte[] encodedState);

  byte[] encode(TopologyChangeStatus topologyChangeStatus);

  TopologyChangeStatus decodeTopologyChangeStatus(byte[] encodedTopologyChangeStatus);
}
