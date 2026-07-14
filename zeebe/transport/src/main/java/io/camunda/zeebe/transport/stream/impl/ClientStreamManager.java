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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ClientStreamManager<M extends BufferWriter> {
  private static final Logger LOG = LoggerFactory.getLogger(ClientStreamManager.class);

  /** physicalTenantId → set of broker member IDs serving that physical tenant */
  private final Map<String, Set<MemberId>> serversByPhysicalTenantId = new HashMap<>();

  /** broker member ID → set of physicalTenantIds it serves (for RESTART lookup) */
  private final Map<MemberId, Set<String>> physicalTenantsByServer = new HashMap<>();

  private final ClientStreamRegistry<M> registry;
  private final ClientStreamRequestManager<M> requestManager;
  private final Function<String, ClientStreamMetrics> metricsFactory;
  private final ClientStreamPusher streamPusher;

  ClientStreamManager(
      final ClientStreamRegistry<M> registry,
      final ClientStreamRequestManager<M> requestManager,
      final Function<String, ClientStreamMetrics> metricsFactory) {
    this.registry = registry;
    this.requestManager = requestManager;
    this.metricsFactory = metricsFactory;
    streamPusher = new ClientStreamPusher(metricsFactory);
  }

  /**
   * Called when a server joins the cluster for a specific partition group. Only streams whose
   * physical tenant matches {@code physicalTenantId} are registered with this server.
   *
   * @param serverId the joining server
   * @param physicalTenantId the physical tenant served by this server
   */
  void onServerJoined(final MemberId serverId, final String physicalTenantId) {
    final var servers =
        serversByPhysicalTenantId.computeIfAbsent(physicalTenantId, id -> new HashSet<>());
    servers.add(serverId);
    physicalTenantsByServer.computeIfAbsent(serverId, id -> new HashSet<>()).add(physicalTenantId);
    metricsFactory.apply(physicalTenantId).serverCount(servers.size());

    registry.list().stream()
        .filter(c -> physicalTenantId.equals(c.physicalTenantId()))
        .forEach(c -> requestManager.add(c, serverId));
  }

  void onServerRemoved(final MemberId serverId) {
    final var physicalTenantIds = physicalTenantsByServer.remove(serverId);
    if (physicalTenantIds != null) {
      physicalTenantIds.forEach(
          physicalTenantId -> {
            final var servers = serversByPhysicalTenantId.get(physicalTenantId);
            if (servers != null) {
              servers.remove(serverId);
              if (servers.isEmpty()) {
                serversByPhysicalTenantId.remove(physicalTenantId);
              }
            }
            metricsFactory
                .apply(physicalTenantId)
                .serverCount(
                    serversByPhysicalTenantId
                        .getOrDefault(physicalTenantId, Collections.emptySet())
                        .size());
          });
    }
    requestManager.onServerRemoved(serverId);
  }

  /**
   * Called when a server that was already in the topology sends a RESTART_STREAMS signal. Resets
   * its registrations and re-registers all streams belonging to its known physical tenants —
   * without touching the topology maps.
   */
  void onServerRestarted(final MemberId serverId) {
    requestManager.onServerRemoved(serverId);
    physicalTenantsByServer
        .getOrDefault(serverId, Collections.emptySet())
        .forEach(
            physicalTenantId ->
                registry.list().stream()
                    .filter(c -> physicalTenantId.equals(c.physicalTenantId()))
                    .forEach(c -> requestManager.add(c, serverId)));
  }

  ClientStreamId add(
      final DirectBuffer streamType,
      final M metadata,
      final ClientStreamConsumer clientStreamConsumer,
      final String physicalTenantId) {
    // add first in memory to handle case of new broker while we're adding
    final var clientStream =
        registry.addClient(streamType, metadata, clientStreamConsumer, physicalTenantId);
    LOG.debug("Added new client stream [{}]", clientStream.streamId());
    final var servers =
        serversByPhysicalTenantId.getOrDefault(physicalTenantId, Collections.emptySet());
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
          final var servers =
              serversByPhysicalTenantId.getOrDefault(
                  stream.physicalTenantId(), Collections.emptySet());
          requestManager.remove(stream, servers);
        });
  }

  void close() {
    registry.clear();
    requestManager.removeAll(serversByPhysicalTenantId);
  }

  public void onPayloadReceived(
      final PushStreamRequest pushStreamRequest, final ActorFuture<Void> responseFuture) {
    final var streamId = pushStreamRequest.streamId();
    final var payload = pushStreamRequest.payload();

    final var clientStream = registry.get(streamId);
    // The wire request carries no physicalTenantId; if the stream was already removed, we cannot
    // attribute this push to any tenant, so the metric is dropped rather than misattributed.
    final var metrics =
        clientStream
            .map(AggregatedClientStream::physicalTenantId)
            .map(metricsFactory)
            .orElseGet(ClientStreamMetrics::noop);

    responseFuture.onComplete(
        (ok, error) -> {
          if (error != null) {
            metrics.pushFailed();
          } else {
            metrics.pushSucceeded();
          }
        });

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
          requestManager.removeUnreliable(streamId, serversByPhysicalTenantId);
          LOG.warn("Expected to push payload to stream {}, but no stream found.", streamId);
          responseFuture.completeExceptionally(
              new NoSuchStreamException(
                  "Cannot forward pushed payload as chosen client stream %s was already closed"
                      .formatted(streamId)));
        });
  }
}
