/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.clustering.base.topology;

import io.atomix.cluster.ClusterMembershipEvent;
import io.atomix.cluster.ClusterMembershipEvent.Type;
import io.atomix.cluster.ClusterMembershipEventListener;
import io.atomix.cluster.Member;
import io.atomix.core.Atomix;
import io.zeebe.broker.Loggers;
import io.zeebe.broker.clustering.base.partitions.RaftState;
import io.zeebe.broker.system.configuration.ClusterCfg;
import io.zeebe.protocol.impl.data.cluster.BrokerInfo;
import io.zeebe.transport.SocketAddress;
import io.zeebe.util.LogUtil;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.future.ActorFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.slf4j.Logger;

public class TopologyManagerImpl extends Actor
    implements TopologyManager, ClusterMembershipEventListener {
  private static final Logger LOG = Loggers.CLUSTERING_LOGGER;

  private final Topology topology;
  private final Atomix atomix;
  private final BrokerInfo distributionInfo;

  private final List<TopologyMemberListener> topologyMemberListeners = new ArrayList<>();
  private final List<TopologyPartitionListener> topologyPartitionListeners = new ArrayList<>();

  public TopologyManagerImpl(Atomix atomix, NodeInfo localBroker, ClusterCfg clusterCfg) {
    this.atomix = atomix;

    // initialize topology
    this.topology =
        new Topology(
            localBroker,
            clusterCfg.getClusterSize(),
            clusterCfg.getPartitionsCount(),
            clusterCfg.getReplicationFactor());
    distributionInfo =
        new BrokerInfo(
            localBroker.getNodeId(),
            topology.getPartitionsCount(),
            topology.getClusterSize(),
            topology.getReplicationFactor());
    distributionInfo.setApiAddress(
        BrokerInfo.CLIENT_API_PROPERTY, localBroker.getClientApiAddress().toString());

    // ensures that the first published event will contain the broker's info
    publishTopologyChanges();
  }

  @Override
  protected void onActorStarted() {
    atomix.getMembershipService().addListener(this);
    atomix
        .getMembershipService()
        .getMembers()
        .forEach(m -> event(new ClusterMembershipEvent(Type.MEMBER_ADDED, m)));
  }

  @Override
  public String getName() {
    return "topology";
  }

  public void updateRole(RaftState state, int partitionId) {
    actor.call(
        () -> {
          final NodeInfo memberInfo = topology.getLocal();

          updatePartition(partitionId, memberInfo, state);
          publishTopologyChanges();
        });
  }

  public void updatePartition(int partitionId, NodeInfo member, RaftState raftState) {
    topology.updatePartition(partitionId, member, raftState);
    notifyPartitionUpdated(partitionId, member);
  }

  @Override
  public void event(ClusterMembershipEvent clusterMembershipEvent) {
    final Member eventSource = clusterMembershipEvent.subject();
    LOG.debug(
        "Member {} received event {}", topology.getLocal().getNodeId(), clusterMembershipEvent);
    final BrokerInfo brokerInfo = readBrokerInfo(eventSource);

    if (brokerInfo != null && brokerInfo.getNodeId() != topology.getLocal().getNodeId()) {
      actor.call(
          () -> {
            switch (clusterMembershipEvent.type()) {
              case METADATA_CHANGED:
                onMetadataChanged(brokerInfo);
                break;

              case MEMBER_ADDED:
                onMemberAdded(brokerInfo);
                onMetadataChanged(brokerInfo);

                break;
              case MEMBER_REMOVED:
                onMemberRemoved(brokerInfo);
                break;
            }
          });
    }
  }

  // Remove a member from the topology
  private void onMemberRemoved(BrokerInfo brokerInfo) {
    final NodeInfo nodeInfo = topology.getMember(brokerInfo.getNodeId());
    if (nodeInfo != null) {
      topology.removeMember(nodeInfo);
      notifyMemberRemoved(nodeInfo);
    }
  }

  // Add a new member to the topology, including its interface's addresses
  private void onMemberAdded(BrokerInfo brokerInfo) {
    final NodeInfo nodeInfo =
        new NodeInfo(
            brokerInfo.getNodeId(),
            SocketAddress.from(brokerInfo.getApiAddress(BrokerInfo.CLIENT_API_PROPERTY)));

    if (topology.addMember(nodeInfo)) {
      notifyMemberAdded(nodeInfo);
    }
  }

  // Update local knowledge about the partitions of remote node
  private void onMetadataChanged(BrokerInfo brokerInfo) {
    final NodeInfo nodeInfo = topology.getMember(brokerInfo.getNodeId());

    brokerInfo.consumePartitions(
        leaderPartitionId -> {
          topology.updatePartition(leaderPartitionId, nodeInfo, RaftState.LEADER);
          notifyPartitionUpdated(leaderPartitionId, nodeInfo);
        },
        followerPartitionId -> {
          topology.updatePartition(followerPartitionId, nodeInfo, RaftState.FOLLOWER);
          notifyPartitionUpdated(followerPartitionId, nodeInfo);
        });
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
        && brokerInfo.getNodeId() < topology.getClusterSize()
        && topology.getClusterSize() == brokerInfo.getClusterSize()
        && topology.getPartitionsCount() == brokerInfo.getPartitionsCount()
        && topology.getReplicationFactor() == brokerInfo.getReplicationFactor();
  }

  // Propagate local partition info to other nodes through Atomix member properties
  private void publishTopologyChanges() {
    final BrokerInfo distributionInfo = createLocalNodeBrokerInfo();
    final Properties memberProperties = atomix.getMembershipService().getLocalMember().properties();
    BrokerInfo.writeIntoProperties(memberProperties, distributionInfo);
  }

  // Transforms the local topology into a the serializable format
  private BrokerInfo createLocalNodeBrokerInfo() {
    final NodeInfo local = topology.getLocal();
    distributionInfo.clearPartitions();

    for (int partitionId : local.getLeaders()) {
      distributionInfo.addLeaderForPartition(partitionId);
    }

    for (int partitionId : local.getFollowers()) {
      distributionInfo.addFollowerForPartition(partitionId);
    }

    return distributionInfo;
  }

  public ActorFuture<Void> close() {
    return actor.close();
  }

  @Override
  public void addTopologyMemberListener(TopologyMemberListener listener) {
    actor.run(
        () -> {
          topologyMemberListeners.add(listener);

          // notify initially
          topology
              .getMembers()
              .forEach((m) -> LogUtil.catchAndLog(LOG, () -> listener.onMemberAdded(m, topology)));
        });
  }

  @Override
  public void removeTopologyMemberListener(TopologyMemberListener listener) {
    actor.run(() -> topologyMemberListeners.remove(listener));
  }

  @Override
  public void addTopologyPartitionListener(TopologyPartitionListener listener) {
    actor.run(
        () -> {
          topologyPartitionListeners.add(listener);

          // notify initially
          topology
              .getPartitions()
              .forEach(
                  (partitionId) ->
                      LogUtil.catchAndLog(
                          LOG,
                          () -> {
                            final NodeInfo leader = topology.getLeader(partitionId);
                            if (leader != null) {
                              listener.onPartitionUpdated(partitionId, leader);
                            }

                            final List<NodeInfo> followers = topology.getFollowers(partitionId);
                            if (followers != null && !followers.isEmpty()) {
                              followers.forEach(
                                  follower -> listener.onPartitionUpdated(partitionId, follower));
                            }
                          }));
        });
  }

  @Override
  public void removeTopologyPartitionListener(TopologyPartitionListener listener) {
    actor.run(() -> topologyPartitionListeners.remove(listener));
  }

  private void notifyMemberAdded(NodeInfo memberInfo) {
    for (TopologyMemberListener listener : topologyMemberListeners) {
      LogUtil.catchAndLog(LOG, () -> listener.onMemberAdded(memberInfo, topology));
    }
  }

  private void notifyMemberRemoved(NodeInfo memberInfo) {
    for (TopologyMemberListener listener : topologyMemberListeners) {
      LogUtil.catchAndLog(LOG, () -> listener.onMemberRemoved(memberInfo, topology));
    }
  }

  private void notifyPartitionUpdated(int partitionId, NodeInfo member) {
    for (TopologyPartitionListener listener : topologyPartitionListeners) {
      LogUtil.catchAndLog(LOG, () -> listener.onPartitionUpdated(partitionId, member));
    }
  }
}
