/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.transport.stream.impl;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.transport.stream.api.ClientStreamConsumer;
import io.camunda.zeebe.transport.stream.api.ClientStreamId;
import io.camunda.zeebe.transport.stream.api.ClientStreamMetrics;
import io.camunda.zeebe.transport.stream.api.NoSuchStreamException;
import io.camunda.zeebe.transport.stream.impl.messages.PushStreamRequest;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.HashSet;
import java.util.Set;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ClientStreamManager<M extends BufferWriter> {
  private static final Logger LOG = LoggerFactory.getLogger(ClientStreamManager.class);
  private final Set<MemberId> servers = new HashSet<>();
  private final ClientStreamRegistry<M> registry;
  private final ClientStreamRequestManager<M> requestManager;
  private final ClientStreamMetrics metrics;
  private final ClientStreamPusher streamPusher;

  ClientStreamManager(
      final ClientStreamRegistry<M> registry,
      final ClientStreamRequestManager<M> requestManager,
      final ClientStreamMetrics metrics) {
    this.registry = registry;
    this.requestManager = requestManager;
    this.metrics = metrics;
    streamPusher = new ClientStreamPusher(metrics);
  }

  /**
   * When a new server is added to the cluster, or an existing server restarted, existing client
   * streams must be registered (again) with the server.
   *
   * @param serverId id of the server that is added or restarted
   */
  void onServerJoined(final MemberId serverId) {
    servers.add(serverId);
    metrics.serverCount(servers.size());

    registry.list().forEach(c -> requestManager.add(c, serverId));
  }

  void onServerRemoved(final MemberId serverId) {
    servers.remove(serverId);
    metrics.serverCount(servers.size());
    requestManager.onServerRemoved(serverId);
  }

  ClientStreamId add(
      final DirectBuffer streamType,
      final M metadata,
      final ClientStreamConsumer clientStreamConsumer) {
    // add first in memory to handle case of new broker while we're adding
    final var clientStream = registry.addClient(streamType, metadata, clientStreamConsumer);
    LOG.debug("Added new client stream [{}]", clientStream.streamId());
    clientStream.serverStream().open(requestManager, servers);

    return clientStream.streamId();
  }

  void remove(final ClientStreamId streamId) {
    LOG.debug("Removing client stream [{}]", streamId);
    final var serverStream = registry.removeClient(streamId);
    serverStream.ifPresent(
        stream -> {
          LOG.debug("Removing aggregated stream [{}]", stream.streamId());
          stream.close();
          requestManager.remove(stream, servers);
        });
  }

  void close() {
    registry.clear();
    requestManager.removeAll(servers);
  }

  public void onPayloadReceived(
      final PushStreamRequest pushStreamRequest, final ActorFuture<Void> responseFuture) {
    final var streamId = pushStreamRequest.streamId();
    final var payload = pushStreamRequest.payload();

    responseFuture.onComplete(
        (ok, error) -> {
          if (error != null) {
            metrics.pushFailed();
          } else {
            metrics.pushSucceeded();
          }
        });

    final var clientStream = registry.get(streamId);
    clientStream.ifPresentOrElse(
        stream -> {
          try {
            streamPusher.push(stream, payload, responseFuture);
          } catch (final Exception e) {
            responseFuture.completeExceptionally(e);
          }
        },
        () -> {
          // Stream does not exist. We expect to have already sent remove request to all servers.
          // But just in case that request is lost, we send remove request again. To keep it simple,
          // we do not retry. Otherwise, it is possible that we send it multiple times unnecessary.
          requestManager.removeUnreliable(streamId, servers);
          LOG.warn("Expected to push payload to stream {}, but no stream found.", streamId);
          responseFuture.completeExceptionally(
              new NoSuchStreamException(
                  "Cannot forward pushed payload as chosen client stream %s was already closed"
                      .formatted(streamId)));
        });
  }
}
