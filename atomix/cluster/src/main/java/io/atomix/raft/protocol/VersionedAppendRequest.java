/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.atomix.raft.protocol;

import io.atomix.cluster.MemberId;
import java.util.List;

public record VersionedAppendRequest(
    int version,
    long term,
    MemberId leader,
    long prevLogIndex,
    long prevLogTerm,
    long commitIndex,
    List<PersistedRaftRecord> entries) {}
