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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.atomix.cluster.ClusterMembershipEvent;
import io.atomix.cluster.ClusterMembershipEventListener;
import io.atomix.cluster.Member;
import io.atomix.core.Atomix;
import io.zeebe.broker.Loggers;
import io.zeebe.broker.system.configuration.ClusterCfg;
import io.zeebe.gateway.impl.broker.cluster.TopologyDistributionInfo;
import io.zeebe.protocol.impl.data.cluster.TopologyResponseDto;
import io.zeebe.raft.Raft;
import io.zeebe.raft.RaftStateListener;
import io.zeebe.raft.state.RaftState;
import io.zeebe.transport.SocketAddress;
import io.zeebe.util.LogUtil;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.future.ActorFuture;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.function.Function;
import org.slf4j.Logger;

public class TopologyManagerImpl extends Actor
    implements TopologyManager, RaftStateListener, ClusterMembershipEventListener {
  private static final Logger LOG = Loggers.CLUSTERING_LOGGER;
  private final Topology topology;
  private final Atomix atomix;
  private final TopologyDistributionInfo distributionInfo;
  private final ObjectMapper mapper = new ObjectMapper();

  private final List<TopologyMemberListener> topologyMemberListers = new ArrayList<>();
  private final List<TopologyPartitionListener> topologyPartitionListers = new ArrayList<>();

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
        new TopologyDistributionInfo(
            localBroker.getNodeId(),
            topology.getPartitionsCount(),
            topology.getClusterSize(),
            topology.getReplicationFactor());
    distributionInfo.setApiAddress("client", localBroker.getClientApiAddress().toString());
    distributionInfo.setApiAddress("management", localBroker.getManagementApiAddress().toString());
    distributionInfo.setApiAddress(
        "replication", localBroker.getReplicationApiAddress().toString());
    distributionInfo.setApiAddress(
        "subscription", localBroker.getSubscriptionApiAddress().toString());

    // ensures that the first published event will contain the broker's info
    publishTopologyChanges();
  }

  @Override
  public String getName() {
    return "topology";
  }

  public void onRaftStarted(Raft raft) {
    actor.run(
        () -> {
          raft.registerRaftStateListener(this);

          onStateChange(raft, raft.getState());
        });
  }

  public void updatePartition(
      int partitionId, int replicationFactor, NodeInfo member, RaftState raftState) {
    final PartitionInfo updatedPartition =
        topology.updatePartition(partitionId, replicationFactor, member, raftState);

    notifyPartitionUpdated(updatedPartition, member);
  }

  public void onRaftRemoved(Raft raft) {
    actor.run(
        () -> {
          final NodeInfo memberInfo = topology.getLocal();

          topology.removePartitionForMember(raft.getPartitionId(), memberInfo);

          raft.unregisterRaftStateListener(this);

          publishTopologyChanges();
        });
  }

  @Override
  public void onStateChange(Raft raft, RaftState raftState) {
    LOG.debug(
        "Raft state changed in node {}  partition {} to {}",
        topology.getLocal().getNodeId(),
        raft.getPartitionId(),
        raft.getState());
    if (raft.getState() == null) {
      return;
    }
    actor.run(
        () -> {
          final NodeInfo memberInfo = topology.getLocal();

          updatePartition(
              raft.getPartitionId(), raft.getReplicationFactor(), memberInfo, raft.getState());

          publishTopologyChanges();
        });
  }

  @Override
  public void event(ClusterMembershipEvent clusterMembershipEvent) {
    actor.call(
        () -> {
          final Member eventSource = clusterMembershipEvent.subject();
          final Member localNode = atomix.getMembershipService().getLocalMember();
          LOG.debug("Member {} received event {}", localNode.id(), clusterMembershipEvent);

          final TopologyDistributionInfo distInfo = extractTopologyFromProperties(eventSource);

          if (distInfo == null || eventSource.id().id().equals(localNode.id().id())) {
            LOG.debug("Member {} ignoring event from {}", localNode.id(), eventSource.id());
            return;
          }

          switch (clusterMembershipEvent.type()) {
            case METADATA_CHANGED:
              onMetadataChanged(distInfo);
              break;

            case MEMBER_ADDED:
              onMemberAdded(distInfo);
              onMetadataChanged(distInfo);

              break;
            case MEMBER_REMOVED:
              onMemberRemoved(distInfo);
              break;
          }
        });
  }

  // Remove a member from the topology
  private void onMemberRemoved(TopologyDistributionInfo distInfo) {
    final NodeInfo nodeInfo = topology.getMember(distInfo.getNodeId());
    if (nodeInfo != null) {
      topology.removeMember(nodeInfo);
      notifyMemberRemoved(nodeInfo);
    }
  }

  // Add a new member to the topology, including its interface's addresses
  private void onMemberAdded(TopologyDistributionInfo distInfo) {
    final NodeInfo nodeInfo =
        new NodeInfo(
            distInfo.getNodeId(),
            SocketAddress.from(distInfo.getApiAddress("client")),
            SocketAddress.from(distInfo.getApiAddress("management")),
            SocketAddress.from(distInfo.getApiAddress("replication")),
            SocketAddress.from(distInfo.getApiAddress("subscription")));

    topology.addMember(nodeInfo);
    notifyMemberAdded(nodeInfo);
  }

  // Update local knowledge about the partitions of remote node
  private void onMetadataChanged(TopologyDistributionInfo distInfo) {
    final NodeInfo nodeInfo = topology.getMember(distInfo.getNodeId());

    for (Integer partitionId : distInfo.getPartitionRoles().keySet()) {
      final RaftState role = distInfo.getPartitionNodeRole(partitionId);

      final PartitionInfo updatedPartition =
          topology.updatePartition(partitionId, topology.getReplicationFactor(), nodeInfo, role);
      notifyPartitionUpdated(updatedPartition, nodeInfo);
    }
  }

  // Extract a topology from the node's properties and, if any exists, validate it
  private TopologyDistributionInfo extractTopologyFromProperties(Member eventSource) {
    final String jsonTopology = eventSource.properties().getProperty("topology");
    if (jsonTopology == null) {
      LOG.debug("Node {} has no topology information", eventSource.id());
      return null;
    }

    final TopologyDistributionInfo distInfo;
    try {
      distInfo = mapper.readValue(jsonTopology, TopologyDistributionInfo.class);
    } catch (IOException e) {
      LOG.error("Error reading topology of node {}. Error: {}", eventSource.id(), e.getMessage());
      return null;
    }

    if (!isStaticConfigValid(distInfo)) {
      LOG.error(
          "Static configuration of node {} differs from local node {}",
          eventSource.id(),
          atomix.getMembershipService().getLocalMember().id());
      return null;
    }

    return distInfo;
  }

  // Validate that the remote node's configuration is equal to the local node
  private boolean isStaticConfigValid(TopologyDistributionInfo distInfo) {
    return distInfo.getNodeId() >= 0
        && distInfo.getNodeId() < topology.getClusterSize()
        && topology.getClusterSize() == distInfo.getClusterSize()
        && topology.getPartitionsCount() == distInfo.getPartitionsCount()
        && topology.getReplicationFactor() == distInfo.getReplicationFactor();
  }

  // Propagate local partition info to other nodes through Atomix member properties
  private void publishTopologyChanges() {
    try {
      final Properties memberProperties =
          atomix.getMembershipService().getLocalMember().properties();
      final TopologyDistributionInfo distributionInfo = createDistributionTopology();
      memberProperties.setProperty("topology", mapper.writeValueAsString(distributionInfo));
    } catch (JsonProcessingException e) {
      LOG.error(
          "{}: Couldn't publish topology information - {}",
          topology.getLocal().getNodeId(),
          e.getMessage());
    }
  }

  // Transforms the local topology into a the serializable format
  private TopologyDistributionInfo createDistributionTopology() {
    final NodeInfo local = topology.getLocal();
    distributionInfo.clearPartitions();

    for (PartitionInfo partitionInfo : topology.getPartitions()) {
      final int partitionId = partitionInfo.getPartitionId();
      final NodeInfo leader = topology.getLeader(partitionId);

      if (leader != null && leader.getNodeId() == local.getNodeId()) {
        distributionInfo.setPartitionRole(partitionId, RaftState.LEADER);
      } else {
        distributionInfo.setPartitionRole(partitionId, RaftState.FOLLOWER);
      }
    }

    return distributionInfo;
  }

  public ActorFuture<Void> close() {
    return actor.close();
  }

  @Override
  public ActorFuture<TopologyResponseDto> getTopologyDto() {
    return actor.call(topology::asDto);
  }

  @Override
  public void addTopologyMemberListener(TopologyMemberListener listener) {
    actor.run(
        () -> {
          topologyMemberListers.add(listener);

          // notify initially
          topology
              .getMembers()
              .forEach(
                  (m) -> {
                    LogUtil.catchAndLog(LOG, () -> listener.onMemberAdded(m, topology));
                  });
        });
  }

  @Override
  public void removeTopologyMemberListener(TopologyMemberListener listener) {
    actor.run(
        () -> {
          topologyMemberListers.remove(listener);
        });
  }

  @Override
  public void addTopologyPartitionListener(TopologyPartitionListener listener) {
    actor.run(
        () -> {
          topologyPartitionListers.add(listener);

          // notify initially
          topology
              .getPartitions()
              .forEach(
                  (p) ->
                      LogUtil.catchAndLog(
                          LOG,
                          () -> {
                            final NodeInfo leader = topology.getLeader(p.getPartitionId());
                            if (leader != null) {
                              listener.onPartitionUpdated(p, leader);
                            }

                            final List<NodeInfo> followers =
                                topology.getFollowers(p.getPartitionId());
                            if (followers != null && !followers.isEmpty()) {
                              followers.forEach(
                                  follower -> listener.onPartitionUpdated(p, follower));
                            }
                          }));
        });
  }

  @Override
  public void removeTopologyPartitionListener(TopologyPartitionListener listener) {
    actor.run(
        () -> {
          topologyPartitionListers.remove(listener);
        });
  }

  private void notifyMemberAdded(NodeInfo memberInfo) {
    for (TopologyMemberListener listener : topologyMemberListers) {
      LogUtil.catchAndLog(LOG, () -> listener.onMemberAdded(memberInfo, topology));
    }
  }

  private void notifyMemberRemoved(NodeInfo memberInfo) {
    for (TopologyMemberListener listener : topologyMemberListers) {
      LogUtil.catchAndLog(LOG, () -> listener.onMemberRemoved(memberInfo, topology));
    }
  }

  private void notifyPartitionUpdated(PartitionInfo partitionInfo, NodeInfo member) {
    for (TopologyPartitionListener listener : topologyPartitionListers) {
      LogUtil.catchAndLog(LOG, () -> listener.onPartitionUpdated(partitionInfo, member));
    }
  }

  @Override
  public <R> ActorFuture<R> query(Function<ReadableTopology, R> query) {
    return actor.call(() -> query.apply(topology));
  }
}
