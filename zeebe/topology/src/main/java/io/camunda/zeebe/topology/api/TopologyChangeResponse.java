/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology.api;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.topology.state.MemberState;
import io.camunda.zeebe.topology.state.TopologyChangeOperation;
import java.util.List;
import java.util.Map;

public record TopologyChangeResponse(
    long changeId,
    Map<MemberId, MemberState> currentTopology,
    Map<MemberId, MemberState> expectedTopology,
    List<TopologyChangeOperation> plannedChanges) {}
