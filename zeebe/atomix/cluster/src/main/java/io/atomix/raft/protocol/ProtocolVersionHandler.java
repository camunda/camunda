/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.raft.protocol;

public final class ProtocolVersionHandler {

  private ProtocolVersionHandler() {
    // To hide the public constructor
  }

  public static InternalAppendRequest transform(final AppendRequest request) {
    return new InternalAppendRequest(
        request.term(),
        request.leader(),
        request.prevLogIndex(),
        request.prevLogTerm(),
        request.commitIndex(),
        request.entries());
  }

  public static InternalAppendRequest transform(final VersionedAppendRequest request) {
    return new InternalAppendRequest(
        request.term(),
        request.leader(),
        request.prevLogIndex(),
        request.prevLogTerm(),
        request.commitIndex(),
        request.entries());
  }
}
