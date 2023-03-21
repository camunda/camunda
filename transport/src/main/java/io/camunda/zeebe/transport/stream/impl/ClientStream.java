/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.transport.stream.impl;

import io.atomix.cluster.MemberId;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class ClientStream {
  private final UUID streamId;
  private final ZpaStream<?, ?> zpaStream;
  private final Set<MemberId> liveConnections = new HashSet<>();

  public ClientStream(final UUID streamId, final ZpaStream<?, ?> zpaStream) {
    this.streamId = streamId;
    this.zpaStream = zpaStream;
  }

  public UUID getStreamId() {
    return streamId;
  }

  public ZpaStream<?, ?> getZpaStream() {
    return zpaStream;
  }

  public void acknowledge(final MemberId serverId) {
    liveConnections.add(serverId);
  }

  public boolean isAcknowledged(final MemberId serverId) {
    return liveConnections.contains(serverId);
  }

  public void removeAcknowledge(final MemberId serverId) {
    liveConnections.remove(serverId);
  }
}
