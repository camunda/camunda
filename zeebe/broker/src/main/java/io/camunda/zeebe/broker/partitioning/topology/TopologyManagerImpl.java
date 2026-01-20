/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.topology;

import io.atomix.cluster.ClusterMembershipEvent;
import io.atomix.cluster.ClusterMembershipEvent.Type;
import io.atomix.cluster.ClusterMembershipEventListener;
import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.Member;
import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.PartitionListener;
import io.camunda.zeebe.engine.state.QueryService;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.protocol.impl.encoding.BrokerInfo;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.util.LogUtil;
import io.camunda.zeebe.util.health.HealthStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;
import org.agrona.collections.Int2ObjectHashMap;
import org.slf4j.Logger;

// TODO: This will be fixed in the https://github.com/zeebe-io/zeebe/issues/5640
public final class TopologyManagerImpl extends Actor
    implements TopologyManager, ClusterMembershipEventListener, PartitionListener {
  private static final Logger LOG = Loggers.CLUSTERING_LOGGER;

  private final Int2ObjectHashMap<BrokerInfo> partitionLeaders = new Int2ObjectHashMap<>();
  private final String partitionGroup;
  private final ClusterMembershipService membershipService;
  private final BrokerInfo localBroker;

  private final List<TopologyPartitionListener> topologyPartitionListeners = new ArrayList<>();
  private final String actorName;

  public TopologyManagerImpl(
      final String partitionGroup,
      final ClusterMembershipService membershipService,
      final BrokerInfo localBroker) {
    this.partitionGroup = partitionGroup;
    this.membershipService = membershipService;
    this.localBroker = localBroker;

    actorName = "TopologyManager";
  }

  @Override
  public ActorFuture<Void> onBecomingFollower(final int partitionId, final long term) {
    return setFollower(partitionId);
  }

  @Override
  public ActorFuture<Void> onBecomingLeader(
      final int partitionId,
      final long term,
      final LogStream logStream,
      final QueryService queryService) {
    return setLeader(term, partitionId);
  }

  @Override
  public ActorFuture<Void> onBecomingInactive(final int partitionId, final long term) {
    return setInactive(partitionId);
  }

  @Override
  public String getName() {
    return actorName;
  }

  @Override
  protected void onActorStarted() {
    // ensures that the first published event will contain the broker's info
    publishTopologyChanges();
    membershipService.addListener(this);
    membershipService
        .getMembers()
        .forEach(m -> event(new ClusterMembershipEvent(Type.MEMBER_ADDED, m)));
  }

  public ActorFuture<Void> setLeader(final long term, final int partitionId) {
    return actor.call(
        () -> {
          partitionLeaders.put(partitionId, localBroker);
          localBroker.setLeaderForPartition(partitionId, term);
          publishTopologyChanges();
          notifyPartitionLeaderUpdated(partitionId, localBroker);
        });
  }

  public ActorFuture<Void> setFollower(final int partitionId) {
    return actor.call(
        () -> {
          removeIfLeader(localBroker, partitionId);
          localBroker.setFollowerForPartition(partitionId);
          publishTopologyChanges();
        });
  }

  public ActorFuture<Void> setInactive(final int partitionId) {
    return actor.call(
        () -> {
          removeIfLeader(localBroker, partitionId);
          localBroker.setInactiveForPartition(partitionId);
          publishTopologyChanges();
        });
  }

  @Override
  public void event(final ClusterMembershipEvent clusterMembershipEvent) {
    final Member eventSource = clusterMembershipEvent.subject();

    final BrokerInfo brokerInfo =
        BrokerInfo.fromProperties(partitionGroup, eventSource.properties());

    if (brokerInfo != null && brokerInfo.getNodeId() != localBroker.getNodeId()) {
      actor.run(
          () -> {
            switch (clusterMembershipEvent.type()) {
              case METADATA_CHANGED:
              case MEMBER_ADDED:
                onMetadataChanged(brokerInfo);
                break;

              case MEMBER_REMOVED:
                onMemberRemoved(brokerInfo);
                break;

              case REACHABILITY_CHANGED:
              default:
                LOG.debug(
                    "Received {} from member {}, was not handled.",
                    clusterMembershipEvent.type(),
                    brokerInfo.getNodeId());
                break;
            }
          });
    }
  }

  // Remove a member from the topology
  private void onMemberRemoved(final BrokerInfo brokerInfo) {
    LOG.debug("Received member removed {} ", brokerInfo);
    brokerInfo.consumePartitions(
        partition -> removeIfLeader(brokerInfo, partition),
        (leaderPartitionId, term) -> {},
        followerPartitionId -> {},
        inactivePartitionId -> {});
  }

  private void removeIfLeader(final BrokerInfo brokerInfo, final Integer partition) {
    final BrokerInfo currentLeader = partitionLeaders.get(partition);
    if (currentLeader != null && currentLeader.getNodeId() == brokerInfo.getNodeId()) {
      partitionLeaders.remove(partition);
    }
  }

  // Update local knowledge about the partitions of remote node
  private void onMetadataChanged(final BrokerInfo brokerInfo) {
    LOG.debug(
        "Received metadata change for {}, partitions {} terms {}",
        brokerInfo.getNodeId(),
        brokerInfo.getPartitionRoles(),
        brokerInfo.getPartitionLeaderTerms());
    brokerInfo.consumePartitions(
        (leaderPartitionId, term) -> {
          if (updatePartitionLeader(brokerInfo, leaderPartitionId, term)) {
            notifyPartitionLeaderUpdated(leaderPartitionId, brokerInfo);
          }
        },
        followerPartitionId -> removeIfLeader(brokerInfo, followerPartitionId),
        inactivePartitionId -> removeIfLeader(brokerInfo, inactivePartitionId));
    // If a partition is removed from the broker, remove it from the local leader list
    final var leaderPartitionsInBroker =
        partitionLeaders.entrySet().stream()
            .filter(
                entry -> {
                  final var broker = entry.getValue();
                  return broker.getNodeId() == brokerInfo.getNodeId();
                })
            .map(Entry::getKey)
            .toList();
    leaderPartitionsInBroker.stream()
        .filter(partitionId -> !brokerInfo.getPartitionRoles().containsKey(partitionId))
        .forEach(removedPartition -> removeIfLeader(brokerInfo, removedPartition));
  }

  private boolean updatePartitionLeader(
      final BrokerInfo brokerInfo, final int leaderPartitionId, final long term) {
    final BrokerInfo currentLeader = partitionLeaders.get(leaderPartitionId);

    if (currentLeader != null) {
      final Long currentLeaderTerm = currentLeader.getPartitionLeaderTerms().get(leaderPartitionId);
      if (currentLeaderTerm == null) {
        LOG.debug(
            "Expected to have a non-null value for current leader term, but found null. Partition {} is likely removed from broker {}. Updating the leader anyway.",
            leaderPartitionId,
            currentLeader.getNodeId());
      } else if (currentLeaderTerm >= term) {
        return false;
      }
    }
    partitionLeaders.put(leaderPartitionId, brokerInfo);
    return true;
  }

  // Propagate local partition info to other nodes through Atomix member properties
  private void publishTopologyChanges() {
    final Properties memberProperties = membershipService.getLocalMember().properties();
    localBroker.writeIntoProperties(partitionGroup, memberProperties);
  }

  @Override
  public void removeTopologyPartitionListener(final TopologyPartitionListener listener) {
    actor.run(() -> topologyPartitionListeners.remove(listener));
  }

  @Override
  public void addTopologyPartitionListener(final TopologyPartitionListener listener) {
    actor.run(
        () -> {
          topologyPartitionListeners.add(listener);

          // notify initially
          partitionLeaders.forEach(
              (partitionId, leader) ->
                  LogUtil.catchAndLog(
                      LOG, () -> listener.onPartitionLeaderUpdated(partitionId, leader)));
        });
  }

  @Override
  public void onHealthChanged(final int partitionId, final HealthStatus status) {
    actor.run(
        () -> {
          if (status == HealthStatus.HEALTHY) {
            localBroker.setPartitionHealthy(partitionId);
          } else if (status == HealthStatus.UNHEALTHY) {
            localBroker.setPartitionUnhealthy(partitionId);
          } else if (status == HealthStatus.DEAD) {
            localBroker.setPartitionDead(partitionId);
          }
          publishTopologyChanges();
        });
  }

  @Override
  public void removePartition(final int partitionId) {
    actor.run(
        () -> {
          removeIfLeader(localBroker, partitionId);
          localBroker.removePartition(partitionId);
        });
  }

  private void notifyPartitionLeaderUpdated(final int partitionId, final BrokerInfo member) {
    for (final TopologyPartitionListener listener : topologyPartitionListeners) {
      LogUtil.catchAndLog(LOG, () -> listener.onPartitionLeaderUpdated(partitionId, member));
    }
  }
}
