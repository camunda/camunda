/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.clustering.topology;

import static io.zeebe.broker.Broker.actorNamePattern;

import io.atomix.cluster.ClusterMembershipEvent;
import io.atomix.cluster.ClusterMembershipEvent.Type;
import io.atomix.cluster.ClusterMembershipEventListener;
import io.atomix.cluster.Member;
import io.atomix.core.Atomix;
import io.zeebe.broker.Loggers;
import io.zeebe.broker.PartitionListener;
import io.zeebe.broker.system.configuration.ClusterCfg;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.protocol.impl.encoding.BrokerInfo;
import io.zeebe.util.LogUtil;
import io.zeebe.util.sched.Actor;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.agrona.collections.Int2ObjectHashMap;
import org.slf4j.Logger;

public class TopologyManagerImpl extends Actor
    implements TopologyManager, ClusterMembershipEventListener, PartitionListener {
  private static final Logger LOG = Loggers.CLUSTERING_LOGGER;

  private final Int2ObjectHashMap<BrokerInfo> partitionLeaders = new Int2ObjectHashMap<>();
  private final Atomix atomix;
  private final BrokerInfo localBroker;

  private final List<TopologyPartitionListener> topologyPartitionListeners = new ArrayList<>();

  public TopologyManagerImpl(Atomix atomix, BrokerInfo localBroker, ClusterCfg clusterCfg) {
    this.atomix = atomix;
    this.localBroker = localBroker;
    localBroker
        .setClusterSize(clusterCfg.getClusterSize())
        .setPartitionsCount(clusterCfg.getPartitionsCount())
        .setReplicationFactor(clusterCfg.getReplicationFactor());
  }

  @Override
  public void onBecomingFollower(int partitionId) {
    setFollower(partitionId);
  }

  @Override
  public void onBecomingLeader(int partitionId, long term, LogStream logStream) {
    setLeader(term, partitionId);
  }

  @Override
  public String getName() {
    return actorNamePattern(localBroker, "TopologyManager");
  }

  @Override
  protected void onActorStarted() {
    // ensures that the first published event will contain the broker's info
    publishTopologyChanges();
    atomix.getMembershipService().addListener(this);
    atomix
        .getMembershipService()
        .getMembers()
        .forEach(m -> event(new ClusterMembershipEvent(Type.MEMBER_ADDED, m)));
  }

  public void setLeader(long term, int partitionId) {
    actor.call(
        () -> {
          partitionLeaders.put(partitionId, localBroker);
          localBroker.setLeaderForPartition(partitionId, term);
          publishTopologyChanges();
          notifyPartitionLeaderUpdated(partitionId, localBroker);
        });
  }

  public void setFollower(int partitionId) {
    actor.call(
        () -> {
          removeIfLeader(localBroker, partitionId);
          localBroker.setFollowerForPartition(partitionId);
          publishTopologyChanges();
        });
  }

  @Override
  public void event(ClusterMembershipEvent clusterMembershipEvent) {
    final Member eventSource = clusterMembershipEvent.subject();

    final BrokerInfo brokerInfo = readBrokerInfo(eventSource);

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
  private void onMemberRemoved(BrokerInfo brokerInfo) {
    LOG.debug("Received member removed {} ", brokerInfo);
    brokerInfo.consumePartitions(
        partition -> removeIfLeader(brokerInfo, partition),
        (leaderPartitionId, term) -> {},
        followerPartitionId -> {});
  }

  private void removeIfLeader(BrokerInfo brokerInfo, Integer partition) {
    final BrokerInfo currentLeader = partitionLeaders.get(partition);
    if (currentLeader != null && currentLeader.getNodeId() == brokerInfo.getNodeId()) {
      partitionLeaders.remove(partition);
    }
  }

  // Update local knowledge about the partitions of remote node
  private void onMetadataChanged(BrokerInfo brokerInfo) {
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
        followerPartitionId -> {
          removeIfLeader(brokerInfo, followerPartitionId);
        });
  }

  private boolean updatePartitionLeader(BrokerInfo brokerInfo, int leaderPartitionId, long term) {
    final BrokerInfo currentLeader = partitionLeaders.get(leaderPartitionId);

    if (currentLeader != null) {
      final Long currentLeaderTerm = currentLeader.getPartitionLeaderTerms().get(leaderPartitionId);
      if (currentLeaderTerm == null) {
        LOG.error(
            "Could not update new leader for partition {} at term {}. Expected to have a non-null value for current leader term, but found null",
            leaderPartitionId,
            term);
        return false;
      }
      if (currentLeaderTerm >= term) {
        return false;
      }
    }
    partitionLeaders.put(leaderPartitionId, brokerInfo);
    return true;
  }

  private BrokerInfo readBrokerInfo(Member eventSource) {
    final BrokerInfo brokerInfo = BrokerInfo.fromProperties(eventSource.properties());
    if (brokerInfo != null && !isStaticConfigValid(brokerInfo)) {
      LOG.error(
          "Static configuration of node {} differs from local node {}",
          eventSource.id(),
          atomix.getMembershipService().getLocalMember().id());
      return null;
    }
    return brokerInfo;
  }

  // Validate that the remote node's configuration is equal to the local node
  private boolean isStaticConfigValid(BrokerInfo brokerInfo) {
    return brokerInfo.getNodeId() >= 0
        && brokerInfo.getNodeId() < localBroker.getClusterSize()
        && localBroker.getClusterSize() == brokerInfo.getClusterSize()
        && localBroker.getPartitionsCount() == brokerInfo.getPartitionsCount()
        && localBroker.getReplicationFactor() == brokerInfo.getReplicationFactor();
  }

  // Propagate local partition info to other nodes through Atomix member properties
  private void publishTopologyChanges() {
    final Properties memberProperties = atomix.getMembershipService().getLocalMember().properties();
    localBroker.writeIntoProperties(memberProperties);
  }

  @Override
  public void removeTopologyPartitionListener(TopologyPartitionListener listener) {
    actor.run(() -> topologyPartitionListeners.remove(listener));
  }

  @Override
  public void addTopologyPartitionListener(TopologyPartitionListener listener) {
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

  private void notifyPartitionLeaderUpdated(int partitionId, BrokerInfo member) {
    for (TopologyPartitionListener listener : topologyPartitionListeners) {
      LogUtil.catchAndLog(LOG, () -> listener.onPartitionLeaderUpdated(partitionId, member));
    }
  }
}
