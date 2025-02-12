/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology.gossip;

import io.atomix.cluster.ClusterMembershipEvent;
import io.atomix.cluster.ClusterMembershipEvent.Type;
import io.atomix.cluster.ClusterMembershipEventListener;
import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.Member;
import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.topology.TopologyUpdateNotifier;
import io.camunda.zeebe.topology.metrics.TopologyMetrics;
import io.camunda.zeebe.topology.serializer.ClusterTopologySerializer;
import io.camunda.zeebe.topology.state.ClusterTopology;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ClusterTopologyGossiper
    implements TopologyUpdateNotifier, ClusterMembershipEventListener, AutoCloseable {
  private static final Logger LOGGER = LoggerFactory.getLogger(ClusterTopologyGossiper.class);
  private static final String SYNC_REQUEST_TOPIC = "cluster-topology-sync";
  private static final String GOSSIP_REQUEST_TOPIC = "cluster-topology-gossip";

  // Each member has a copy of this gossipState.
  private final ClusterTopologyGossipState gossipState = new ClusterTopologyGossipState();
  private final ConcurrencyControl executor;
  private final ClusterCommunicationService communicationService;
  private final ClusterMembershipService membershipService;
  private final ClusterTopologyGossiperConfig config;
  private final ClusterTopologySerializer serializer;

  private final Set<TopologyUpdateListener> topologyUpdateListeners = new HashSet<>();

  // Reuse the same random ordered list for both sync and gossip
  private List<MemberId> membersToSync = new LinkedList<>();

  // The handler which can merge topology updates and reacts to the changes.
  private final Consumer<ClusterTopology> clusterTopologyUpdateHandler;
  private final TopologyMetrics topologyMetrics;

  public ClusterTopologyGossiper(
      final ConcurrencyControl executor,
      final ClusterCommunicationService communicationService,
      final ClusterMembershipService membershipService,
      final ClusterTopologySerializer serializer,
      final ClusterTopologyGossiperConfig config,
      final Consumer<ClusterTopology> clusterTopologyUpdateHandler,
      final TopologyMetrics topologyMetrics) {
    this.executor = executor;
    this.communicationService = communicationService;
    this.membershipService = membershipService;
    this.config = config;
    this.serializer = serializer;
    this.clusterTopologyUpdateHandler = clusterTopologyUpdateHandler;
    this.topologyMetrics = topologyMetrics;
  }

  public CompletableActorFuture<Void> start() {
    final var startedFuture = new CompletableActorFuture<Void>();
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
    registerMemberAddedListener();
  }

  private void registerMemberAddedListener() {
    membershipService.addListener(this);
  }

  private void unregisterMemberListener() {
    membershipService.removeListener(this);
  }

  private void registerSyncHandler() {
    communicationService.replyTo(
        SYNC_REQUEST_TOPIC,
        serializer::decode,
        this::handleSyncRequest,
        serializer::encode,
        executor::run);
  }

  private void unregisterSyncHandler() {
    communicationService.unsubscribe(SYNC_REQUEST_TOPIC);
  }

  private void registerGossipHandler() {
    communicationService.consume(
        GOSSIP_REQUEST_TOPIC, serializer::decode, this::handleGossip, executor::run);
  }

  private void unregisterGossipHandler() {
    communicationService.unsubscribe(GOSSIP_REQUEST_TOPIC);
  }

  private void scheduleSync() {
    if (config.enableSync()) {
      executor.schedule(config.syncDelay(), this::sync);
    }
  }

  private void sync() {
    refreshMembersToSync();

    if (membersToSync.isEmpty()) {
      return;
    }

    final var randomMemberToSync = membersToSync.remove(0);

    sync(randomMemberToSync);
  }

  private void sync(final MemberId toMember) {
    LOGGER.trace("Sending sync request to {}", toMember);
    sendSyncRequest(toMember)
        .whenCompleteAsync(
            (response, error) -> handleSyncResponse(response, error, toMember), executor::run);
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
      update(response);
    } else {
      LOGGER.warn("Failed to sync with {}", member, error);
    }
    scheduleSync();
  }

  private void update(final ClusterTopologyGossipState receivedGossipState) {
    if (!receivedGossipState.equals(gossipState)) {
      final ClusterTopology topology = receivedGossipState.getClusterTopology();
      if (topology != null) {
        clusterTopologyUpdateHandler.accept(topology);
      }
    }
  }

  private void onTopologyUpdated(final ClusterTopology updatedTopology) {
    gossipState.setClusterTopology(updatedTopology);
    LOGGER.trace("Updated local gossipState to {}", updatedTopology);
    gossip();
    notifyListeners(updatedTopology);
    topologyMetrics.updateFromTopology(updatedTopology);
  }

  private void notifyListeners(final ClusterTopology updatedTopology) {
    topologyUpdateListeners.forEach(listener -> listener.onTopologyUpdated(updatedTopology));
  }

  private ClusterTopologyGossipState handleSyncRequest(
      final MemberId memberId, final ClusterTopologyGossipState clusterSharedGossipState) {
    LOGGER.trace(
        "Received topology sync request from {} with state {}", memberId, clusterSharedGossipState);
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
            onTopologyUpdated(clusterTopology);
          }
        });
  }

  public ActorFuture<ClusterTopology> queryClusterTopology(final MemberId memberId) {
    final ActorFuture<ClusterTopology> responseFuture = executor.createFuture();
    sendSyncRequest(memberId)
        .whenCompleteAsync(
            (response, error) -> {
              if (error == null) {
                responseFuture.complete(response.getClusterTopology());
              } else {
                responseFuture.completeExceptionally(error);
              }
            },
            executor::run);
    return responseFuture;
  }

  private CompletableFuture<ClusterTopologyGossipState> sendSyncRequest(final MemberId memberId) {
    return communicationService.send(
        SYNC_REQUEST_TOPIC,
        gossipState,
        serializer::encode,
        serializer::decode,
        memberId,
        config.syncRequestTimeout());
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
    update(receivedState);
  }

  @Override
  public void addUpdateListener(final TopologyUpdateListener listener) {
    executor.run(
        () -> {
          topologyUpdateListeners.add(listener);
          if (gossipState.getClusterTopology() != null) {
            listener.onTopologyUpdated(gossipState.getClusterTopology());
          }
        });
  }

  @Override
  public void removeUpdateListener(final TopologyUpdateListener listener) {
    executor.run(() -> topologyUpdateListeners.remove(listener));
  }

  @Override
  public boolean isRelevant(final ClusterMembershipEvent event) {
    return event.type() == Type.MEMBER_ADDED || event.type() == Type.MEMBER_REMOVED;
  }

  @Override
  public void event(final ClusterMembershipEvent event) {
    switch (event.type()) {
      case MEMBER_ADDED ->
          // When a new member is added to the cluster, immediately sync with it so that the new
          // member
          // receives the latest topology as fast as possible.
          executor.run(
              () -> {
                if (config.enableSync()) {
                  sync(event.subject().id());
                }
              });
      case MEMBER_REMOVED ->
          // When a member is removed, remove it from the list of members to sync so that we don't
          // try
          // to sync with it in the next round. This is only for optimization.
          executor.run(() -> membersToSync.remove(event.subject().id()));
      default -> {
        // ignore
      }
    }
  }

  @Override
  public void close() {
    unregisterMemberListener();
    unregisterSyncHandler();
    unregisterGossipHandler();
  }
}
