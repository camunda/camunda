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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.atomix.cluster.ClusterMembershipEvent;
import io.atomix.cluster.ClusterMembershipEventListener;
import io.atomix.cluster.Member;
import io.atomix.core.Atomix;
import io.zeebe.broker.Loggers;
import io.zeebe.broker.system.configuration.ClusterCfg;
import io.zeebe.protocol.impl.data.cluster.TopologyResponseDto;
import io.zeebe.raft.Raft;
import io.zeebe.raft.RaftStateListener;
import io.zeebe.raft.state.RaftState;
import io.zeebe.transport.SocketAddress;
import io.zeebe.util.LogUtil;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.future.ActorFuture;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.function.Function;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;

public class TopologyManagerImpl extends Actor
    implements TopologyManager, RaftStateListener, ClusterMembershipEventListener {
  private static final Logger LOG = Loggers.CLUSTERING_LOGGER;

  public static final DirectBuffer CONTACT_POINTS_EVENT_TYPE =
      BufferUtil.wrapString("contact_points");

  private final Topology topology;
  private final Atomix atomix;

  private final ObjectMapper mapper = new ObjectMapper();

  private final List<TopologyMemberListener> topologyMemberListers = new ArrayList<>();
  private final List<TopologyPartitionListener> topologyPartitionListers = new ArrayList<>();

  public TopologyManagerImpl(Atomix atomix, NodeInfo localBroker, ClusterCfg clusterCfg) {
    this.atomix = atomix;
    this.topology =
        new Topology(
            localBroker,
            clusterCfg.getClusterSize(),
            clusterCfg.getPartitionsCount(),
            clusterCfg.getReplicationFactor());
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

          publishLocalPartitions();
        });
  }

  @Override
  public void onStateChange(Raft raft, RaftState raftState) {
    LOG.info(
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

          publishLocalPartitions();
        });
  }

  @Override
  public void event(ClusterMembershipEvent clusterMembershipEvent) {

    final Member eventSource = clusterMembershipEvent.subject();
    final Member localNode = atomix.getMembershipService().getLocalMember();

    if (localNode.id().equals(eventSource.id())) {
      // don't process events from myself
      return;
    }

    LOG.info(
        "Member {} receives event {}",
        atomix.getMembershipService().getLocalMember().id(),
        clusterMembershipEvent);

    actor.call(
        () -> {
          switch (clusterMembershipEvent.type()) {
            case METADATA_CHANGED:
              onMemberMetadataChanged(clusterMembershipEvent);
              break;
            case MEMBER_ADDED:
              onMemberAdded(clusterMembershipEvent);
              break;
            case MEMBER_REMOVED:
              onMemberRemoved(clusterMembershipEvent);
              break;
            default:
              LOG.info(
                  "Im node {}, event received from {} {}. Nothing to do",
                  localNode.id(),
                  eventSource.id(),
                  clusterMembershipEvent.type());
          }
        });
  }

  private void onMemberMetadataChanged(ClusterMembershipEvent clusterMembershipEvent) {
    final Member eventSource = clusterMembershipEvent.subject();
    final Member localNode = atomix.getMembershipService().getLocalMember();

    LOG.info("Member {} process metadata change of member {}", localNode.id(), eventSource.id());

    updatePartitionInfo(eventSource);
  }

  private void onMemberRemoved(ClusterMembershipEvent clusterMembershipEvent) {
    final Member eventSource = clusterMembershipEvent.subject();
    final Member localNode = atomix.getMembershipService().getLocalMember();

    LOG.info("Member {} process event member {} removed", localNode.id(), eventSource.id());

    final NodeInfo nodeInfo = topology.getMember(Integer.parseInt(eventSource.id().id()));
    if (nodeInfo != null) {
      topology.removeMember(nodeInfo);
      notifyMemberRemoved(nodeInfo);
    }
  }

  private void onMemberAdded(ClusterMembershipEvent clusterMembershipEvent) {
    final Member eventSource = clusterMembershipEvent.subject();
    final Member localNode = atomix.getMembershipService().getLocalMember();

    LOG.info("Member {} process event member {} added", localNode.id(), eventSource.id());

    final Properties newProperties = eventSource.properties();
    final String replicationAddress = newProperties.getProperty("replicationAddress");
    final String managementAddress = newProperties.getProperty("managementAddress");
    final String clientApiAddress = newProperties.getProperty("clientAddress");
    final String subscriptionAddress = newProperties.getProperty("subscriptionAddress");
    try {
      final InetSocketAddress replication =
          mapper.readValue(replicationAddress, InetSocketAddress.class);
      final InetSocketAddress management =
          mapper.readValue(managementAddress, InetSocketAddress.class);
      final InetSocketAddress client = mapper.readValue(clientApiAddress, InetSocketAddress.class);
      final InetSocketAddress subscription =
          mapper.readValue(subscriptionAddress, InetSocketAddress.class);

      final NodeInfo nodeInfo =
          new NodeInfo(
              Integer.parseInt(eventSource.id().id()),
              new SocketAddress(client),
              new SocketAddress(management),
              new SocketAddress(replication),
              new SocketAddress(subscription));

      topology.addMember(nodeInfo);
      notifyMemberAdded(nodeInfo);
      updatePartitionInfo(eventSource);

    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  // Update local knowledge about the partitions of remote node
  private void updatePartitionInfo(Member node) {
    final Properties properties = node.properties();
    final NodeInfo nodeInfo = topology.getMember(Integer.parseInt(node.id().id()));
    for (String p : properties.stringPropertyNames()) {
      if (p.startsWith("partition")) {
        final int partitionId = Integer.parseInt(p.split("-")[1]);
        final PartitionInfo partitionInfo =
            topology.updatePartition(
                partitionId,
                0, // ignore replicationFactor
                nodeInfo,
                RaftState.valueOf(properties.getProperty(p)));

        notifyPartitionUpdated(partitionInfo, nodeInfo);
      }
    }
  }

  // propagate local partition info to other nodes
  private void publishLocalPartitions() {
    final Properties memberProperties = atomix.getMembershipService().getLocalMember().properties();
    for (PartitionInfo p : topology.getLocal().getLeaders()) {
      memberProperties.setProperty("partition-" + p.getPartitionId(), RaftState.LEADER.toString());
    }
    for (PartitionInfo p : topology.getLocal().getFollowers()) {
      memberProperties.setProperty(
          "partition-" + p.getPartitionId(), RaftState.FOLLOWER.toString());
    }
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
