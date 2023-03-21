/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.transport.stream.impl;

import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.transport.stream.impl.messages.AddStreamRequest;
import io.camunda.zeebe.transport.stream.impl.messages.RemoveStreamRequest;
import io.camunda.zeebe.transport.stream.impl.messages.StreamTopics;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.time.Duration;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.agrona.concurrent.UnsafeBuffer;

public final class RemoteStreamClient extends Actor {

  private static final byte[] REMOVE_ALL_REQUEST = new byte[0];
  private final Set<MemberId> brokerIds;
  private final ClusterCommunicationService communicationService;
  private final ClusterMembershipService membershipService;

  public RemoteStreamClient(
      final Set<MemberId> brokerIds,
      final ClusterCommunicationService communicationService,
      final ClusterMembershipService membershipService) {
    this.brokerIds = brokerIds;
    this.communicationService = communicationService;
    this.membershipService = membershipService;
  }

  public ClientStream add(final ClientStream clientStream, final Collection<MemberId> servers) {
    final var request =
        new AddStreamRequest()
            .streamType(clientStream.getZpaStream().streamType())
            .streamId(clientStream.getStreamId())
            .metadata(clientStream.getZpaStream().metadata());

    brokerIds.forEach(brokerId -> actor.run(() -> doAdd(request, brokerId, clientStream)));

    return clientStream;
  }

  private void doAdd(
      final AddStreamRequest request, final MemberId brokerId, final ClientStream clientStream) {
    if (clientStream.isAcknowledged(brokerId)) {
      return;
    }

    final CompletableFuture<byte[]> result =
        communicationService.send(
            StreamTopics.ADD.topic(),
            request,
            this::serialize,
            Function.identity(),
            brokerId,
            Duration.ofSeconds(5));

    result.whenComplete(
        (ignored, error) -> {
          if (clientStream.isAcknowledged(brokerId)) {
            return;
          }

          // TODO: log on proper error, etc., find the actual abort condition
          if (error != null && membershipService.getMember(brokerId) != null) {
            actor.runDelayed(Duration.ofSeconds(1), () -> doAdd(request, brokerId, clientStream));
          } else {
            clientStream.acknowledge(brokerId);
          }
        });
  }

  public void remove(final ClientStream clientStream) {
    final var request = new RemoveStreamRequest().streamId(clientStream.getStreamId());
    brokerIds.forEach(brokerId -> actor.run(() -> doRemove(request, brokerId, clientStream)));
  }

  private void doRemove(
      final RemoveStreamRequest request, final MemberId brokerId, final ClientStream clientStream) {
    final CompletableFuture<byte[]> result =
        communicationService.send(
            StreamTopics.REMOVE.topic(),
            request,
            this::serialize,
            Function.identity(),
            brokerId,
            Duration.ofSeconds(5));

    result.whenComplete(
        (ignored, error) -> {
          // TODO: log on proper error, etc., find the actual abort condition
          if (error != null
              && membershipService.getMember(brokerId) != null
              && clientStream.isAcknowledged(brokerId)) {
            actor.runDelayed(
                Duration.ofSeconds(1), () -> doRemove(request, brokerId, clientStream));
          } else {
            clientStream.removeAcknowledge(brokerId);
          }
        });
  }

  public void removeAll() {
    brokerIds.forEach(brokerId -> actor.run(() -> doRemoveAll(brokerId)));
  }

  private void doRemoveAll(final MemberId brokerId) {
    final CompletableFuture<byte[]> result =
        communicationService.send(
            StreamTopics.REMOVE_ALL.topic(),
            REMOVE_ALL_REQUEST,
            Function.identity(),
            Function.identity(),
            brokerId,
            Duration.ofSeconds(5));
  }

  private byte[] serialize(final BufferWriter payload) {
    final var bytes = new byte[payload.getLength()];
    final var writeBuffer = new UnsafeBuffer();
    writeBuffer.wrap(bytes);

    payload.write(writeBuffer, 0);
    return bytes;
  }
}
