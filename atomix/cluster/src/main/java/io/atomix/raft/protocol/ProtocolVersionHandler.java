/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.atomix.raft.protocol;

public final class ProtocolVersionHandler {

  private static final int VERSION_APPENDREQUEST = 1;
  private static final int VERSION_APPENDREQUEST_V2 = 2;

  private ProtocolVersionHandler() {
    // override public one
  }

  public static VersionedAppendRequest transform(final AppendRequest request) {
    return new VersionedAppendRequest(
        VERSION_APPENDREQUEST,
        request.term(),
        request.leader(),
        request.prevLogIndex(),
        request.prevLogTerm(),
        request.commitIndex(),
        request.entries());
  }

  public static VersionedAppendRequest transform(final AppendRequestV2 request) {
    return new VersionedAppendRequest(
        VERSION_APPENDREQUEST_V2,
        request.term(),
        request.leader(),
        request.prevLogIndex(),
        request.prevLogTerm(),
        request.commitIndex(),
        request.entries());
  }
}
