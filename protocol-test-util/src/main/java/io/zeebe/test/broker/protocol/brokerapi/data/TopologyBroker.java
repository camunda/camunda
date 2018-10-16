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

import io.zeebe.transport.SocketAddress;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public class TopologyBroker {
  private Set<BrokerPartitionState> partitions = new LinkedHashSet<>();

  protected final int nodeId;
  protected final String host;
  protected final int port;
  private SocketAddress address;

  public TopologyBroker(final int nodeId, final String host, final int port) {
    this.nodeId = nodeId;
    this.host = host;
    this.port = port;
    address = new SocketAddress(host, port);
  }

  public int getNodeId() {
    return nodeId;
  }

  public String getHost() {
    return host;
  }

  public int getPort() {
    return port;
  }

  public Set<BrokerPartitionState> getPartitions() {
    return partitions;
  }

  public TopologyBroker addPartition(BrokerPartitionState brokerPartitionState) {
    partitions.add(brokerPartitionState);
    return this;
  }

  public SocketAddress getAddress() {
    return address;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final TopologyBroker that = (TopologyBroker) o;
    return nodeId == that.nodeId;
  }

  @Override
  public int hashCode() {
    return Objects.hash(nodeId);
  }

  @Override
  public String toString() {
    return "TopologyBroker{"
        + "partitions="
        + partitions
        + ", nodeId="
        + nodeId
        + ", host='"
        + host
        + '\''
        + ", port="
        + port
        + ", address="
        + address
        + '}';
  }
}
