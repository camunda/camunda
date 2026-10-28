/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.impl;

import com.google.protobuf.InvalidProtocolBufferException;
import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationCoordinatorSupplier;
import io.camunda.zeebe.dynamic.config.impl.protocol.Requests.FetchPhysicalTenantConfigRequest;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.PhysicalTenantConfigReference;
import java.time.Duration;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gets the configuration of a physical tenant that a reference names: from this member's {@link
 * PhysicalTenantConfigStore} if it holds it, otherwise from the first peer that answers with bytes
 * matching the reference's hash, which it then stores. Also answers the same request from peers.
 */
@NullMarked
public final class PhysicalTenantConfigFetcher implements AutoCloseable {

  static final String TOPIC = "physical-tenant-config-fetch";
  private static final Logger LOG = LoggerFactory.getLogger(PhysicalTenantConfigFetcher.class);
  private static final Duration TIMEOUT = Duration.ofSeconds(10);

  private final ClusterCommunicationService communicationService;
  private final PhysicalTenantConfigStore store;

  public PhysicalTenantConfigFetcher(
      final ClusterCommunicationService communicationService,
      final PhysicalTenantConfigStore store) {
    this.communicationService = communicationService;
    this.store = store;
  }

  public void start() {
    communicationService.replyTo(
        TOPIC,
        PhysicalTenantConfigFetcher::decode,
        request ->
            CompletableFuture.completedFuture(
                store.get(request.getPhysicalTenantId(), request.getVersion()).orElse(new byte[0])),
        Function.identity());
  }

  /**
   * @param peers the members to ask, in order, if this member does not hold the configuration
   * @return the configuration, verified against the reference's hash and stored on this member
   */
  public CompletableFuture<byte[]> fetch(
      final String physicalTenantId,
      final PhysicalTenantConfigReference reference,
      final List<MemberId> peers) {
    final var local = store.get(physicalTenantId, reference.version(), reference.sha256());
    if (local.isPresent()) {
      return CompletableFuture.completedFuture(local.get());
    }
    final var request =
        FetchPhysicalTenantConfigRequest.newBuilder()
            .setPhysicalTenantId(physicalTenantId)
            .setVersion(reference.version())
            .build();
    return askNext(physicalTenantId, reference, request, peers.iterator());
  }

  private CompletableFuture<byte[]> askNext(
      final String physicalTenantId,
      final PhysicalTenantConfigReference reference,
      final FetchPhysicalTenantConfigRequest request,
      final Iterator<MemberId> peers) {
    if (!peers.hasNext()) {
      return CompletableFuture.failedFuture(
          new IllegalStateException(
              "No reachable member holds version %d of the configuration of physical tenant '%s'"
                  .formatted(reference.version(), physicalTenantId)));
    }
    final var peer = peers.next();
    return communicationService
        .<FetchPhysicalTenantConfigRequest, byte[]>send(
            TOPIC,
            request,
            FetchPhysicalTenantConfigRequest::toByteArray,
            Function.identity(),
            peer,
            TIMEOUT)
        .handle(
            (configuration, error) -> {
              if (error == null
                  && PhysicalTenantConfigStore.sha256(configuration).equals(reference.sha256())) {
                store.put(physicalTenantId, reference.version(), configuration);
                return CompletableFuture.completedFuture(configuration);
              }
              LOG.debug(
                  "Member {} did not provide version {} of the configuration of physical tenant"
                      + " '{}'",
                  peer.id(),
                  reference.version(),
                  physicalTenantId,
                  error);
              return askNext(physicalTenantId, reference, request, peers);
            })
        .thenCompose(Function.identity());
  }

  /**
   * The members to ask for a configuration, in order: the coordinator, which stored it when it
   * accepted the change, then the members that already applied it, then the others.
   */
  public static List<MemberId> peersToAsk(
      final GlobalConfiguration global,
      final PhysicalTenantConfigReference reference,
      final MemberId localMemberId) {
    final var coordinator =
        ClusterConfigurationCoordinatorSupplier.ofMembers(global.members().keySet())
            .getDefaultCoordinator();
    return global.members().keySet().stream()
        .filter(member -> !member.equals(localMemberId))
        .sorted(
            Comparator.comparing((MemberId member) -> !member.equals(coordinator))
                .thenComparing(member -> reference.appliedVersion(member) < reference.version())
                .thenComparing(MemberId.ID_COMPARATOR))
        .toList();
  }

  @Override
  public void close() {
    communicationService.unsubscribe(TOPIC);
  }

  private static FetchPhysicalTenantConfigRequest decode(final byte[] bytes) {
    try {
      return FetchPhysicalTenantConfigRequest.parseFrom(bytes);
    } catch (final InvalidProtocolBufferException e) {
      throw new IllegalArgumentException(e);
    }
  }
}
