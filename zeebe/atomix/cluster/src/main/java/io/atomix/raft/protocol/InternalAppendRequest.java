/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.raft.protocol;

import io.atomix.cluster.MemberId;
import java.util.List;

/** Used by RaftRoles to handle AppendRequest from multiple versions uniformly. */
public record InternalAppendRequest(
    long term,
    MemberId leader,
    long prevLogIndex,
    long prevLogTerm,
    long commitIndex,
    List<? extends ReplicatableRaftRecord> entries) {}
