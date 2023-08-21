/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology.gossip;

import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.Member;
import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.topology.state.ClusterTopology;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ClusterTopologyGossiper {
  private static final Logger LOGGER = LoggerFactory.getLogger(ClusterTopologyGossiper.class);
  private static final String SYNC_REQUEST_TOPIC = "cluster-topology-sync";
  private static final String GOSSIP_REQUEST_TOPIC = "cluster-topology-gossip";

  // Each member has a copy of this gossipState.
  private final ClusterTopologyGossipState gossipState = new ClusterTopologyGossipState();
  private final ConcurrencyControl executor;
  private final ClusterCommunicationService communicationService;
  private final ClusterMembershipService membershipService;
  private final ClusterTopologyGossiperConfig config;
  private final ClusterTopologyGossipSerializer serializer;

  // Reuse the same random ordered list for both sync and gossip
  private List<MemberId> membersToSync = List.of();

  // The handler which can merge topology updates and reacts to the changes.
  private final Function<ClusterTopology, ActorFuture<ClusterTopology>>
      clusterTopologyUpdateHandler;

  public ClusterTopologyGossiper(
      final ConcurrencyControl executor,
      final ClusterCommunicationService communicationService,
      final ClusterMembershipService membershipService,
      final ClusterTopologyGossipSerializer serializer,
      final ClusterTopologyGossiperConfig config,
      final Function<ClusterTopology, ActorFuture<ClusterTopology>> clusterTopologyUpdateHandler) {
    this.executor = executor;
    this.communicationService = communicationService;
    this.membershipService = membershipService;
    this.config = config;
    this.serializer = serializer;
    this.clusterTopologyUpdateHandler = clusterTopologyUpdateHandler;
  }

  public ActorFuture<Void> start() {
    final ActorFuture<Void> startedFuture = executor.createFuture();
    executor.run(
        () -> {
          internalStart();
          startedFuture.complete(null);
        });
    return startedFuture;
  }

  private void internalStart() {
    scheduleSync();
    registerSyncHandler();
    registerGossipHandler();
  }

  private void registerSyncHandler() {
    communicationService.replyTo(
        SYNC_REQUEST_TOPIC,
        serializer::decode,
        this::handleSyncRequest,
        serializer::encode,
        executor::run);
  }

  private void registerGossipHandler() {
    communicationService.consume(
        GOSSIP_REQUEST_TOPIC, serializer::decode, this::handleGossip, executor::run);
  }

  private void scheduleSync() {
    executor.schedule(config.syncDelay(), this::sync);
  }

  private void sync() {
    refreshMembersToSync();

    if (membersToSync.isEmpty()) {
      return;
    }

    final var randomMemberToSync = membersToSync.remove(0);

    LOGGER.trace("Sending sync request to {}", randomMemberToSync);
    communicationService
        .send(
            SYNC_REQUEST_TOPIC,
            gossipState,
            serializer::encode,
            serializer::decode,
            randomMemberToSync,
            config.syncRequestTimeout())
        .whenCompleteAsync(
            (response, error) -> handleSyncResponse(response, error, randomMemberToSync),
            executor::run);
  }

  private void refreshMembersToSync() {
    if (membersToSync.isEmpty()) {
      membersToSync =
          membershipService.getMembers().stream()
              .map(Member::id)
              .filter(id -> !id.equals(membershipService.getLocalMember().id()))
              .collect(Collectors.toCollection(LinkedList::new));
      Collections.shuffle(membersToSync);
    }
  }

  private void handleSyncResponse(
      final ClusterTopologyGossipState response, final Throwable error, final MemberId member) {
    if (error == null) {
      update(response, () -> executor.run(this::gossip));
    } else {
      LOGGER.warn("Failed to sync with {}", member, error);
    }
    scheduleSync();
  }

  private void update(
      final ClusterTopologyGossipState receivedGossipState, final Runnable onUpdated) {
    if (!receivedGossipState.equals(gossipState)) {
      final ClusterTopology topology = receivedGossipState.getClusterTopology();
      if (topology != null) {
        final var topologyUpdateFuture = clusterTopologyUpdateHandler.apply(topology);
        topologyUpdateFuture.onComplete(
            (updatedTopology, error) -> {
              if (error != null) {
                LOGGER.warn("Failed to process cluster topology received via gossip", error);
              } else if (!updatedTopology.equals(gossipState.getClusterTopology())) {
                gossipState.setClusterTopology(updatedTopology);
                LOGGER.trace("Updated local gossipState to {}", updatedTopology);
                onUpdated.run();
              }
            });
      }
    }
  }

  private ClusterTopologyGossipState handleSyncRequest(
      final MemberId memberId, final ClusterTopologyGossipState clusterSharedGossipState) {
    LOGGER.trace(
        "Received topology sync request from {} with state {}", memberId, clusterSharedGossipState);
    update(clusterSharedGossipState, () -> executor.run(this::gossip));
    return gossipState;
  }

  public void updateClusterTopology(final ClusterTopology clusterTopology) {
    if (clusterTopology == null) {
      return;
    }
    executor.run(
        () -> {
          if (!clusterTopology.equals(gossipState.getClusterTopology())) {
            gossipState.setClusterTopology(clusterTopology);
            gossip();
          }
        });
  }

  private void gossip() {
    // TODO: Instead of selecting random members, we can also propagate via a tree topology to
    // prevent duplicate gossip updates
    refreshMembersToSync();
    if (membersToSync.isEmpty()) {
      return;
    }
    final var gossipMembersList =
        membersToSync.subList(0, Math.min(config.gossipFanout(), membersToSync.size()));
    LOGGER.trace("Gossiping {} to {}", gossipState, gossipMembersList);
    gossipMembersList.forEach(
        member ->
            communicationService.unicast(
                GOSSIP_REQUEST_TOPIC, gossipState, serializer::encode, member, true));
    // The list is backed by `membersToSync`. After gossip we remove them from the list so that in
    // the next try it chooses a different set of members
    gossipMembersList.clear();
  }

  private void handleGossip(
      final MemberId memberId, final ClusterTopologyGossipState receivedState) {
    LOGGER.trace("Received {} from {}", gossipState, memberId);
    update(receivedState, () -> executor.run(this::gossip));
  }
}
