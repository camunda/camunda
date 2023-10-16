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
public final class TopologyManagementRequests {
  public record AddMembersRequest(Set<MemberId> members) {}
}
