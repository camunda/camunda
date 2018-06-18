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

import static io.zeebe.broker.clustering.base.gossip.GossipCustomEventEncoding.*;

import io.zeebe.broker.Loggers;
import io.zeebe.gossip.Gossip;
import io.zeebe.gossip.GossipCustomEventListener;
import io.zeebe.gossip.GossipMembershipListener;
import io.zeebe.gossip.GossipSyncRequestHandler;
import io.zeebe.gossip.dissemination.GossipSyncRequest;
import io.zeebe.gossip.membership.Member;
import io.zeebe.raft.Raft;
import io.zeebe.raft.RaftStateListener;
import io.zeebe.raft.state.RaftState;
import io.zeebe.transport.SocketAddress;
import io.zeebe.util.LogUtil;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.future.ActorFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.slf4j.Logger;

public class TopologyManagerImpl extends Actor implements TopologyManager, RaftStateListener {
  private static final Logger LOG = Loggers.CLUSTERING_LOGGER;

  public static final DirectBuffer CONTACT_POINTS_EVENT_TYPE =
      BufferUtil.wrapString("contact_points");
  public static final DirectBuffer PARTITIONS_EVENT_TYPE = BufferUtil.wrapString("partitions");

  private final MembershipListener membershipListner = new MembershipListener();
  private final ContactPointsChangeListener contactPointsChangeListener =
      new ContactPointsChangeListener();
  private final PartitionChangeListener partitionChangeListener = new PartitionChangeListener();
  private final KnownContactPointsSyncHandler localContactPointsSycHandler =
      new KnownContactPointsSyncHandler();
  private final KnownPartitionsSyncHandler knownPartitionsSyncHandler =
      new KnownPartitionsSyncHandler();

  private final Topology topology;
  private final Gossip gossip;

  private List<TopologyMemberListener> topologyMemberListers = new ArrayList<>();
  private List<TopologyPartitionListener> topologyPartitionListers = new ArrayList<>();

  public TopologyManagerImpl(Gossip gossip, NodeInfo localBroker) {
    this.gossip = gossip;
    this.topology = new Topology(localBroker);
  }

  @Override
  public String getName() {
    return "topology";
  }

  @Override
  protected void onActorStarting() {
    gossip.addMembershipListener(membershipListner);

    gossip.addCustomEventListener(CONTACT_POINTS_EVENT_TYPE, contactPointsChangeListener);
    gossip.addCustomEventListener(PARTITIONS_EVENT_TYPE, partitionChangeListener);

    // publishing should be done before registering sync handler, since
    // we can only handle sync requests if we published the custom event type before
    publishLocalContactPoints();

    gossip.registerSyncRequestHandler(CONTACT_POINTS_EVENT_TYPE, localContactPointsSycHandler);
    gossip.registerSyncRequestHandler(PARTITIONS_EVENT_TYPE, knownPartitionsSyncHandler);
  }

  @Override
  protected void onActorClosing() {
    gossip.removeCustomEventListener(partitionChangeListener);
    gossip.removeCustomEventListener(contactPointsChangeListener);

    // remove gossip sync handlers?
  }

  public void onRaftStarted(Raft raft) {
    actor.run(
        () -> {
          raft.registerRaftStateListener(this);

          onStateChange(raft, raft.getState());
        });
  }

  public void updatePartition(
      int partitionId,
      DirectBuffer topicBuffer,
      int replicationFactor,
      NodeInfo member,
      RaftState raftState) {
    final PartitionInfo updatedPartition =
        topology.updatePartition(partitionId, topicBuffer, replicationFactor, member, raftState);

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
    actor.run(
        () -> {
          final NodeInfo memberInfo = topology.getLocal();

          updatePartition(
              raft.getPartitionId(),
              raft.getTopicName(),
              raft.getReplicationFactor(),
              memberInfo,
              raft.getState());

          publishLocalPartitions();
        });
  }

  private class ContactPointsChangeListener implements GossipCustomEventListener {
    @Override
    public void onEvent(SocketAddress sender, DirectBuffer payload) {
      final SocketAddress senderCopy = new SocketAddress(sender);
      final DirectBuffer payloadCopy = BufferUtil.cloneBuffer(payload);

      actor.run(
          () -> {
            LOG.trace("Received API event from member {}.", senderCopy);

            int offset = 0;

            final SocketAddress managementApi = new SocketAddress();
            offset = readSocketAddress(offset, payloadCopy, managementApi);

            final SocketAddress clientApi = new SocketAddress();
            offset = readSocketAddress(offset, payloadCopy, clientApi);

            final SocketAddress replicationApi = new SocketAddress();
            readSocketAddress(offset, payloadCopy, replicationApi);

            final NodeInfo newMember = new NodeInfo(clientApi, managementApi, replicationApi);
            topology.addMember(newMember);
            notifyMemberAdded(newMember);
          });
    }
  }

  private class MembershipListener implements GossipMembershipListener {
    @Override
    public void onAdd(Member member) {
      // noop; we listen on the availability of contact points, see ContactPointsChangeListener
    }

    @Override
    public void onRemove(Member member) {
      final NodeInfo topologyMember = topology.getMemberByManagementApi(member.getAddress());
      if (topologyMember != null) {
        topology.removeMember(topologyMember);
        notifyMemberRemoved(topologyMember);
      }
    }
  }

  private class PartitionChangeListener implements GossipCustomEventListener {
    @Override
    public void onEvent(SocketAddress sender, DirectBuffer payload) {
      final SocketAddress senderCopy = new SocketAddress(sender);
      final DirectBuffer payloadCopy = BufferUtil.cloneBuffer(payload);

      actor.run(
          () -> {
            final NodeInfo member = topology.getMemberByManagementApi(senderCopy);

            if (member != null) {
              readPartitions(payloadCopy, 0, member, TopologyManagerImpl.this);
              LOG.trace("Received raft state change event for member {} {}", senderCopy, member);
            } else {
              LOG.trace("Received raft state change event for unknown member {}", senderCopy);
            }
          });
    }
  }

  private class KnownContactPointsSyncHandler implements GossipSyncRequestHandler {
    private final ExpandableArrayBuffer writeBuffer = new ExpandableArrayBuffer();

    @Override
    public ActorFuture<Void> onSyncRequest(GossipSyncRequest request) {
      return actor.call(
          () -> {
            LOG.trace("Got API sync request");

            for (NodeInfo member : topology.getMembers()) {
              final int length = writeSockedAddresses(member, writeBuffer, 0);
              request.addPayload(member.getManagementApiAddress(), writeBuffer, 0, length);
            }

            LOG.trace("Send API sync response.");
          });
    }
  }

  private class KnownPartitionsSyncHandler implements GossipSyncRequestHandler {
    private final ExpandableArrayBuffer writeBuffer = new ExpandableArrayBuffer();

    @Override
    public ActorFuture<Void> onSyncRequest(GossipSyncRequest request) {
      return actor.call(
          () -> {
            LOG.trace("Got RAFT state sync request.");

            for (NodeInfo member : topology.getMembers()) {
              final int length = writePartitions(member, writeBuffer, 0);
              request.addPayload(member.getManagementApiAddress(), writeBuffer, 0, length);
            }

            LOG.trace("Send RAFT state sync response.");
          });
    }
  }

  private void publishLocalContactPoints() {
    final MutableDirectBuffer eventBuffer = new ExpandableArrayBuffer();
    final int eventLength = writeSockedAddresses(topology.getLocal(), eventBuffer, 0);

    gossip.publishEvent(CONTACT_POINTS_EVENT_TYPE, eventBuffer, 0, eventLength);
  }

  private void publishLocalPartitions() {
    final MutableDirectBuffer eventBuffer = new ExpandableArrayBuffer();
    final int length = writePartitions(topology.getLocal(), eventBuffer, 0);

    gossip.publishEvent(PARTITIONS_EVENT_TYPE, eventBuffer, 0, length);
  }

  public ActorFuture<Void> close() {
    return actor.close();
  }

  @Override
  public ActorFuture<TopologyDto> getTopologyDto() {
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
