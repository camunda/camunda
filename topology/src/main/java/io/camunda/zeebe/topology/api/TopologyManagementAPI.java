/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology.api;

import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.topology.api.TopologyManagementRequests.AddMembersRequest;
import io.camunda.zeebe.topology.api.TopologyManagementResponses.TopologyChangeStatus;

/** Defines the API for the topology management requests. */
public interface TopologyManagementAPI {

  ActorFuture<TopologyChangeStatus> addMembers(AddMembersRequest addMembersRequest);
}
