/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.transport.fetch;

import io.atomix.cluster.messaging.ManagedPayload;
import io.netty.buffer.ByteBuf;
import java.util.List;

public class ManagedFetchResponseAdapter implements ManagedPayload {

  private final FetchResponse response;

  public ManagedFetchResponseAdapter(final FetchResponse response) {
    this.response = response;
  }

  @Override
  public int length() {
    // TODO: SBE
    return response.dataLength();
  }

  @Override
  public void encode(final ByteBuf buffer, final List<Object> out) {
    final var ranges = response.entries();
    final var channel = response.channel();
    for (final var indexEntry : ranges) {
      out.add(new SharedFileRegion(channel, indexEntry.position(), indexEntry.length()));
    }
  }

  @Override
  public void release() {
    response.releaseLease();
  }
}
