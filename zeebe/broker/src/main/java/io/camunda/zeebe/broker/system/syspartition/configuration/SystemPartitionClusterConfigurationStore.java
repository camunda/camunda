/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.syspartition.configuration;

import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.zeebe.broker.system.syspartition.configuration.ClusterConfigurationStoreProtocol.CasRequest;
import io.camunda.zeebe.broker.system.syspartition.configuration.ClusterConfigurationStoreProtocol.Response;
import io.camunda.zeebe.broker.system.syspartition.configuration.ClusterConfigurationStoreProtocol.Status;
import io.camunda.zeebe.dynamic.config.ClusterConfigurationStore;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.ConcurrentModificationException;
import io.camunda.zeebe.dynamic.config.serializer.ProtoBufSerializer;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Reads and compare-and-sets the cluster configuration stored in the system partition, from any
 * broker. Requests go to the system partition leader, found by trying the members in turn, the last
 * known leader first, in rounds until one answers, as the partition may still be electing one.
 */
public final class SystemPartitionClusterConfigurationStore implements ClusterConfigurationStore {
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(2);
  private static final Duration RETRY_DELAY = Duration.ofMillis(200);
  // below the timeout of management requests sent to the coordinator, so that it can still reply
  private static final Duration LEADER_LOOKUP_TIMEOUT = Duration.ofSeconds(8);

  private final ClusterCommunicationService communicationService;
  private final List<MemberId> members;
  private final ProtoBufSerializer serializer = new ProtoBufSerializer();
  private volatile MemberId lastKnownLeader;

  public SystemPartitionClusterConfigurationStore(
      final ClusterCommunicationService communicationService,
      final Collection<MemberId> systemPartitionMembers) {
    this.communicationService = communicationService;
    members = systemPartitionMembers.stream().sorted().toList();
  }

  @Override
  public CompletableFuture<Optional<CurrentClusterConfiguration>> read() {
    return sendToLeader(ClusterConfigurationStoreProtocol.READ_SUBJECT, new byte[0])
        .thenApply(
            response -> {
              final var payload = okPayload(response);
              return payload.length == 0
                  ? Optional.empty()
                  : Optional.of(serializer.decodeCurrentClusterConfiguration(payload));
            });
  }

  @Override
  public CompletableFuture<Void> compareAndSet(
      final long expectedNextId, final CurrentClusterConfiguration configuration) {
    final var request =
        new CasRequest(expectedNextId, serializer.encodeCurrentClusterConfiguration(configuration));
    return sendToLeader(ClusterConfigurationStoreProtocol.CAS_SUBJECT, request.encode())
        .thenAccept(
            response -> {
              if (response.status() == Status.REJECTED) {
                throw new ConcurrentModificationException(
                    "Cannot apply configuration change. Another coordinator changed the"
                        + " configuration concurrently: "
                        + response.message());
              }
              okPayload(response);
            });
  }

  private static byte[] okPayload(final Response response) {
    if (response.status() != Status.OK) {
      throw new IllegalStateException(
          "Expected the system partition leader to answer, but it failed: " + response.message());
    }
    return response.payload();
  }

  private CompletableFuture<Response> sendToLeader(final String subject, final byte[] request) {
    final var candidates = new ArrayList<MemberId>();
    final var leader = lastKnownLeader;
    if (leader != null) {
      candidates.add(leader);
    }
    members.stream().filter(member -> !member.equals(leader)).forEach(candidates::add);
    final var result = new CompletableFuture<Response>();
    final var deadline = System.nanoTime() + LEADER_LOOKUP_TIMEOUT.toNanos();
    sendToNextCandidate(subject, request, candidates, 0, deadline, result);
    return result;
  }

  private void sendToNextCandidate(
      final String subject,
      final byte[] request,
      final List<MemberId> candidates,
      final int index,
      final long deadline,
      final CompletableFuture<Response> result) {
    if (index >= candidates.size()) {
      if (System.nanoTime() - deadline >= 0) {
        result.completeExceptionally(
            new IllegalStateException(
                "Expected to reach the system partition leader among %s within %s, but none answered"
                    .formatted(candidates, LEADER_LOOKUP_TIMEOUT)));
        return;
      }
      CompletableFuture.delayedExecutor(RETRY_DELAY.toMillis(), TimeUnit.MILLISECONDS)
          .execute(() -> sendToNextCandidate(subject, request, candidates, 0, deadline, result));
      return;
    }
    final var candidate = candidates.get(index);
    communicationService
        .<byte[], Response>send(
            subject, request, bytes -> bytes, Response::decode, candidate, REQUEST_TIMEOUT)
        .whenComplete(
            (response, error) -> {
              if (error != null || response.status() == Status.NOT_LEADER) {
                sendToNextCandidate(subject, request, candidates, index + 1, deadline, result);
                return;
              }
              lastKnownLeader = candidate;
              result.complete(response);
            });
  }
}
