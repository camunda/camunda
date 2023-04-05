/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.transport.stream.impl;

import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** A registry to keeps tracks of all open streams. */
final class ClientStreamRegistry<M extends BufferWriter> {

  // This class is currently a very simple wrapper around a map. When we aggregate multiple streams
  // into one stream, we may have to keep track of them also here.
  private final Map<UUID, ClientStream<M>> streams = new HashMap<>();

  void add(final ClientStream<M> clientStream) {
    streams.put(clientStream.getStreamId(), clientStream);
  }

  Optional<ClientStream<M>> get(final UUID streamId) {
    return Optional.ofNullable(streams.get(streamId));
  }

  Optional<ClientStream<M>> remove(final UUID streamId) {
    return Optional.ofNullable(streams.remove(streamId));
  }

  Collection<ClientStream<M>> list() {
    return streams.values();
  }
}
