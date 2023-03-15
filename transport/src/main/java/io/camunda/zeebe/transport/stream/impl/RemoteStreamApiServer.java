/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.transport.stream.impl;

import io.atomix.cluster.ClusterMembershipEvent;
import io.atomix.cluster.ClusterMembershipEvent.Type;
import io.atomix.cluster.ClusterMembershipEventListener;
import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.zeebe.protocol.impl.stream.AddStreamRequest;
import io.camunda.zeebe.protocol.impl.stream.JobStreamTopics;
import io.camunda.zeebe.protocol.impl.stream.RemoveStreamRequest;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.util.buffer.BufferReader;
import java.util.function.Function;
import org.agrona.concurrent.UnsafeBuffer;

public final class RemoteStreamApiServer<M extends BufferReader> extends Actor
    implements ClusterMembershipEventListener {
  private final ClusterCommunicationService transport;
  private final RemoteStreamApiHandler<M> requestHandler;

  public RemoteStreamApiServer(
      final ClusterCommunicationService transport, final RemoteStreamApiHandler<M> requestHandler) {
    this.transport = transport;
    this.requestHandler = requestHandler;
  }

  @Override
  public boolean isRelevant(final ClusterMembershipEvent event) {
    return event.type() == Type.MEMBER_REMOVED;
  }

  @Override
  public void event(final ClusterMembershipEvent event) {
    actor.run(() -> requestHandler.removeAll(event.subject().id()));
  }

  @Override
  protected void onActorStarting() {
    transport.subscribe(
        JobStreamTopics.ADD.topic(), this::parseAddRequest, requestHandler::add, actor::run);
    transport.subscribe(
        JobStreamTopics.REMOVE.topic(),
        this::parseRemoveRequest,
        requestHandler::remove,
        actor::run);
    transport.subscribe(
        JobStreamTopics.REMOVE_ALL.topic(), Function.identity(), this::onRemoveAll, actor::run);
  }

  @Override
  protected void onActorClosing() {
    transport.unsubscribe(JobStreamTopics.ADD.topic());
    transport.unsubscribe(JobStreamTopics.REMOVE.topic());
    transport.unsubscribe(JobStreamTopics.REMOVE_ALL.topic());
    requestHandler.close();
  }

  private void onRemoveAll(final MemberId sender, final byte[] ignored) {
    requestHandler.removeAll(sender);
  }

  private RemoveStreamRequest parseRemoveRequest(final byte[] bytes) {
    return parseRequest(bytes, new RemoveStreamRequest());
  }

  private AddStreamRequest parseAddRequest(final byte[] bytes) {
    return parseRequest(bytes, new AddStreamRequest());
  }

  private <R extends BufferReader> R parseRequest(final byte[] bytes, final R request) {
    final var buffer = new UnsafeBuffer(bytes);
    request.wrap(buffer, 0, buffer.capacity());

    return request;
  }
}
