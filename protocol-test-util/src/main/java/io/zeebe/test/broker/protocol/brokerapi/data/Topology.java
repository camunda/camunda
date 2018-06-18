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
package io.zeebe.test.broker.protocol.brokerapi.data;

import static io.zeebe.test.broker.protocol.brokerapi.data.BrokerPartitionState.FOLLOWER_STATE;
import static io.zeebe.test.broker.protocol.brokerapi.data.BrokerPartitionState.LEADER_STATE;

import io.zeebe.test.broker.protocol.brokerapi.StubBrokerRule;
import io.zeebe.transport.SocketAddress;
import java.util.*;

public class Topology {

  protected Map<SocketAddress, TopologyBroker> brokers = new HashMap<>();

  public Topology() {}

  public Topology(Topology other) {
    this.brokers = new HashMap<>(other.brokers);
  }

  private TopologyBroker getBroker(String host, int port) {
    final SocketAddress brokerAddress = new SocketAddress(host, port);
    TopologyBroker topologyBroker = brokers.get(brokerAddress);
    if (topologyBroker == null) {
      topologyBroker = new TopologyBroker(host, port);
      brokers.put(brokerAddress, topologyBroker);
    }
    return topologyBroker;
  }

  public Topology addLeader(String host, int port, String topic, int partition) {
    getBroker(host, port).addPartition(new BrokerPartitionState(LEADER_STATE, topic, partition));

    return this;
  }

  public Topology addFollower(String host, int port, String topic, int partition) {
    getBroker(host, port).addPartition(new BrokerPartitionState(FOLLOWER_STATE, topic, partition));

    return this;
  }

  public Topology addLeader(StubBrokerRule broker, String topic, int partition) {
    return addLeader(broker.getHost(), broker.getPort(), topic, partition);
  }

  public Set<TopologyBroker> getBrokers() {
    return new HashSet<>(brokers.values());
  }

  @Override
  public String toString() {
    return "Topology{" + "brokers=" + brokers.values() + '}';
  }
}
