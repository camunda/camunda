/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.transport.stream.impl;

import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.transport.stream.impl.messages.AddStreamRequest;
import io.camunda.zeebe.transport.stream.impl.messages.MessageUtil;
import io.camunda.zeebe.transport.stream.impl.messages.RemoveStreamRequest;
import io.camunda.zeebe.transport.stream.impl.messages.StreamTopics;
import io.camunda.zeebe.util.buffer.BufferReader;
import java.util.function.Function;

public final class RemoteStreamEndpoint<M extends BufferReader> extends Actor {
  private static final byte[] EMPTY_RESPONSE = new byte[0];

  private final ClusterCommunicationService transport;
  private final RemoteStreamApiHandler<M> requestHandler;

  public RemoteStreamEndpoint(
      final ClusterCommunicationService transport, final RemoteStreamApiHandler<M> requestHandler) {
    this.transport = transport;
    this.requestHandler = requestHandler;
  }

  @Override
  protected void onActorStarting() {
    transport.replyTo(
        StreamTopics.ADD.topic(),
        MessageUtil::parseAddRequest,
        this::onAdd,
        Function.identity(),
        actor::run);
    transport.replyTo(
        StreamTopics.REMOVE.topic(),
        MessageUtil::parseRemoveRequest,
        this::onRemove,
        Function.identity(),
        actor::run);
    transport.replyTo(
        StreamTopics.REMOVE_ALL.topic(),
        Function.identity(),
        this::onRemoveAll,
        Function.identity(),
        actor::run);
  }

  @Override
  protected void onActorClosing() {
    transport.unsubscribe(StreamTopics.ADD.topic());
    transport.unsubscribe(StreamTopics.REMOVE.topic());
    transport.unsubscribe(StreamTopics.REMOVE_ALL.topic());
    requestHandler.close();
  }

  public void removeAll(final MemberId member) {
    actor.run(() -> requestHandler.removeAll(member));
  }

  private byte[] onAdd(final MemberId sender, final AddStreamRequest request) {
    requestHandler.add(sender, request);
    return EMPTY_RESPONSE;
  }

  private byte[] onRemove(final MemberId sender, final RemoveStreamRequest request) {
    requestHandler.remove(sender, request);
    return EMPTY_RESPONSE;
  }

  private byte[] onRemoveAll(final MemberId sender, final byte[] ignored) {
    requestHandler.removeAll(sender);
    return EMPTY_RESPONSE;
  }
}
