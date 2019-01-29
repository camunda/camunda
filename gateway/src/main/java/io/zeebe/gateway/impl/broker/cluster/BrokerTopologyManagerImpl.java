/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.gateway.impl.broker.cluster;

import static io.zeebe.gateway.impl.broker.BrokerClientImpl.LOG;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.atomix.cluster.ClusterMembershipEvent;
import io.atomix.cluster.ClusterMembershipEvent.Type;
import io.atomix.cluster.ClusterMembershipEventListener;
import io.atomix.cluster.Member;
import io.zeebe.raft.state.RaftState;
import io.zeebe.transport.ClientOutput;
import io.zeebe.transport.SocketAddress;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.future.ActorFuture;
import java.io.IOException;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

public class BrokerTopologyManagerImpl extends Actor
    implements BrokerTopologyManager, ClusterMembershipEventListener {
  protected final ClientOutput output;
  protected final BiConsumer<Integer, SocketAddress> registerEndpoint;
  protected final AtomicReference<BrokerClusterStateImpl> topology;
  private final ObjectMapper objectMapper;
  private String atomixMemberId;

  public BrokerTopologyManagerImpl(
      final ClientOutput output,
      final BiConsumer<Integer, SocketAddress> registerEndpoint,
      String atomixMemberId) {
    this.output = output;
    this.registerEndpoint = registerEndpoint;
    this.atomixMemberId = atomixMemberId;

    this.objectMapper = new ObjectMapper();
    this.topology = new AtomicReference<>(new BrokerClusterStateImpl());
  }

  public void addInitialCluster(Set<Member> initialCluster) {
    actor.submit(
        () -> {
          LOG.debug("{}: Setting up initial state", atomixMemberId);

          for (Member broker : initialCluster) {
            onMembershipEvent(Type.MEMBER_ADDED, broker);
          }
        });
  }

  public ActorFuture<Void> close() {
    return actor.close();
  }

  /** @return the current known cluster state or null if the topology was not fetched yet */
  @Override
  public BrokerClusterState getTopology() {
    return topology.get();
  }

  @Override
  public void event(ClusterMembershipEvent event) {
    actor.call(
        () -> {
          LOG.debug("{}: Received event: {}", atomixMemberId, event);
          onMembershipEvent(event.type(), event.subject());
        });
  }

  private void onMembershipEvent(Type eventType, Member subject) {
    final Properties properties = subject.properties();
    final String memberId = subject.id().id();
    final int brokerId;

    try {
      brokerId = Integer.parseInt(memberId);
    } catch (NumberFormatException e) {
      LOG.debug("{}: Ignoring member named '{}'", atomixMemberId, memberId);
      return;
    }

    final BrokerClusterStateImpl newTopology = new BrokerClusterStateImpl(topology.get());
    switch (eventType) {
      case MEMBER_ADDED:
        newTopology.addBrokerIfAbsent(brokerId);
        processProperties(properties, brokerId, newTopology);
        break;

      case METADATA_CHANGED:
        processProperties(properties, brokerId, newTopology);
        break;

      case MEMBER_REMOVED:
        newTopology.removeBroker(brokerId);
        processProperties(properties, brokerId, newTopology);
        break;
    }

    topology.set(newTopology);
  }

  /*
   * Processes properties and updates the topology accordingly. Can be safely used by both
   * MEMBER_ADDED and MEMBER_REMOVED since, if a broker is not in the topology, updates related to
   * it will not be made.
   */
  private void processProperties(
      Properties properties, int brokerId, BrokerClusterStateImpl newTopology) {
    for (String propertyName : properties.stringPropertyNames()) {
      final String propertyValue = properties.getProperty(propertyName);

      if (propertyName.startsWith("partition-")) {
        updatePartitions(propertyName, propertyValue, brokerId, newTopology);
      } else if (propertyName.equals("clientAddress")) {
        updateBrokerClientAddress(propertyValue, brokerId, newTopology);
      } else {
        updateClusterInfo(propertyName, propertyValue, brokerId, newTopology);
      }
    }
  }

  private void updatePartitions(
      String propertyName, String propertyValue, int brokerId, BrokerClusterStateImpl newTopology) {
    final int partitionId = Integer.parseInt(propertyName.split("-")[1]);
    newTopology.addPartitionIfAbsent(partitionId);

    if (RaftState.valueOf(propertyValue) == RaftState.LEADER) {
      newTopology.setPartitionLeader(partitionId, brokerId);
      LOG.debug(
          "{}: Added broker {} as leader of partition {}", atomixMemberId, brokerId, partitionId);
    }
  }

  private void updateBrokerClientAddress(
      String clientApiAddress, int brokerId, BrokerClusterStateImpl newTopology) {
    try {
      final String socketAddress = objectMapper.readValue(clientApiAddress, String.class);
      registerEndpoint.accept(brokerId, SocketAddress.from(socketAddress));
      newTopology.setBrokerAddressIfPresent(brokerId, socketAddress);
    } catch (IOException e) {
      LOG.error("{}: Invalid client address for broker {}:  ", atomixMemberId, brokerId, e);
    }
  }

  private void updateClusterInfo(
      String propertyName, String propertyValue, int brokerId, BrokerClusterStateImpl newTopology) {

    try {
      switch (propertyName) {
        case "replicationFactor":
          newTopology.setReplicationFactor(Integer.parseInt(propertyValue));
          break;

        case "clusterSize":
          newTopology.setClusterSize(Integer.parseInt(propertyValue));
          break;

        case "partitionsCount":
          newTopology.setPartitionsCount(Integer.parseInt(propertyValue));
          break;
      }

    } catch (NumberFormatException e) {
      LOG.debug(
          "{}: broker {} has invalid property '{}' of value '{}'",
          atomixMemberId,
          brokerId,
          propertyName,
          propertyValue);
    }
  }
}
