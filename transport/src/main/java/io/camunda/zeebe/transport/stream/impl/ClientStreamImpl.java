/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.transport.stream.impl;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.transport.stream.api.ClientStream;
import io.camunda.zeebe.transport.stream.api.ClientStreamConsumer;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.Set;
import org.agrona.DirectBuffer;

/** Represents a registered client stream. * */
record ClientStreamImpl<M extends BufferWriter>(
    ClientStreamIdImpl streamId,
    AggregatedClientStream<M> serverStream,
    DirectBuffer streamType,
    M metadata,
    ClientStreamConsumer clientStreamConsumer)
    implements ClientStream<M> {

  ActorFuture<Void> push(final DirectBuffer payload) {
    try {
      return clientStreamConsumer.push(payload);
    } catch (final Exception e) {
      return CompletableActorFuture.completedExceptionally(e);
    }
  }

  @Override
  public Set<MemberId> liveConnections() {
    return serverStream().liveConnections();
  }
}
