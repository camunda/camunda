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
import io.camunda.zeebe.topology.state.ClusterTopology;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.function.UnaryOperator;

public final class ClusterTopologyGossiper {
  private static final String SYNC_REQUEST_TOPIC = "cluster-topology-sync";
  private static final String GOSSIP_REQUEST_TOPIC = "cluster-topology-gossip";

  // Each member has a copy of this gossipState.
  private final ClusterTopologyGossipState gossipState = new ClusterTopologyGossipState();
  private final ConcurrencyControl executor;
  private final ClusterCommunicationService communicationService;
  private final ClusterMembershipService membershipService;
  private final ClusterTopologyGossiperConfig config;
  private final ClusterTopologyGossipSerializer serializer;

  private List<MemberId> reachableMembers = List.of();

  // The handler which can merge topology updates and reacts to the changes.
  private final UnaryOperator<ClusterTopology> clusterTopologyUpdateHandler;

  public ClusterTopologyGossiper(
      final ConcurrencyControl executor,
      final ClusterCommunicationService communicationService,
      final ClusterMembershipService membershipService,
      final ClusterTopologyGossipSerializer serializer,
      final ClusterTopologyGossiperConfig config,
      final UnaryOperator<ClusterTopology> clusterTopologyUpdateHandler) {
    this.executor = executor;
    this.communicationService = communicationService;
    this.membershipService = membershipService;
    this.config = config;
    this.serializer = serializer;
    this.clusterTopologyUpdateHandler = clusterTopologyUpdateHandler;
  }

  public void start() {
    executor.run(this::internalStart);
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
    refreshReachableMembers();

    if (reachableMembers.isEmpty()) {
      return;
    }

    final var randomMemberToSync = reachableMembers.remove(0);
    communicationService
        .send(
            SYNC_REQUEST_TOPIC,
            gossipState,
            serializer::encode,
            serializer::decode,
            randomMemberToSync,
            config.syncRequestTimeout())
        .whenCompleteAsync(this::handleSyncResponse, executor::run);
  }

  private void refreshReachableMembers() {
    if (reachableMembers.isEmpty()) {
      reachableMembers =
          new LinkedList<>(membershipService.getMembers().stream().map(Member::id).toList());
      Collections.shuffle(reachableMembers);
    }
  }

  private void handleSyncResponse(
      final ClusterTopologyGossipState response, final Throwable error) {
    if (error == null) {
      update(response);
    }
    scheduleSync();
  }

  // returns true if local state is changed
  private boolean update(final ClusterTopologyGossipState response) {
    if (!response.equals(gossipState)) {
      final var updatedTopology = clusterTopologyUpdateHandler.apply(response.getClusterTopology());
      gossipState.setClusterTopology(updatedTopology);
      return true;
    }

    return false;
  }

  private ClusterTopologyGossipState handleSyncRequest(
      final MemberId memberId, final ClusterTopologyGossipState clusterSharedGossipState) {
    update(clusterSharedGossipState);
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
    // Instead of selecting random members, we can also propagate via a tree topology to prevent
    // duplicate gossip updates
    refreshReachableMembers();
    if (reachableMembers.isEmpty()) {
      return;
    }
    final var gossipMembersList =
        reachableMembers.subList(0, Math.min(config.gossipFanout(), reachableMembers.size()));
    gossipMembersList.forEach(
        member ->
            communicationService.unicast(
                SYNC_REQUEST_TOPIC, gossipState, serializer::encode, member, false));
    gossipMembersList.clear();
  }

  private void handleGossip(final ClusterTopologyGossipState receivedState) {
    if (update(receivedState)) {
      // forward update to next set of members
      executor.run(this::gossip);
    }
  }
}
