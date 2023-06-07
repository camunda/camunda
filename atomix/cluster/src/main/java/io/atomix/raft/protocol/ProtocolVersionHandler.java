/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.atomix.raft.protocol;

public final class ProtocolVersionHandler {

  private static final int APPENDREQUEST_WITH_RAFTRECORDS = 1;

  private ProtocolVersionHandler() {
    // To hide the public constructor
  }

  public static InternalAppendRequest transform(final AppendRequest request) {
    return new InternalAppendRequest(
        APPENDREQUEST_WITH_RAFTRECORDS,
        request.term(),
        request.leader(),
        request.prevLogIndex(),
        request.prevLogTerm(),
        request.commitIndex(),
        request.entries());
  }

  public static InternalAppendRequest transform(final VersionedAppendRequest request) {
    return new InternalAppendRequest(
        request.version(),
        request.term(),
        request.leader(),
        request.prevLogIndex(),
        request.prevLogTerm(),
        request.commitIndex(),
        request.entries());
  }
}
