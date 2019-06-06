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
package io.zeebe.client.impl;

import io.zeebe.client.api.commands.BrokerInfo;
import io.zeebe.client.api.commands.PartitionBrokerRole;
import io.zeebe.client.api.commands.PartitionInfo;
import io.zeebe.gateway.protocol.GatewayOuterClass;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BrokerInfoImpl implements BrokerInfo {

  private final int nodeId;
  private final String host;
  private final int port;
  private final List<PartitionInfo> partitions;

  public BrokerInfoImpl(final GatewayOuterClass.BrokerInfo broker) {
    this.nodeId = broker.getNodeId();
    this.host = broker.getHost();
    this.port = broker.getPort();

    this.partitions = new ArrayList<>();
    for (final GatewayOuterClass.Partition partition : broker.getPartitionsList()) {
      this.partitions.add(new PartitionInfoImpl(partition));
    }
  }

  @Override
  public int getNodeId() {
    return nodeId;
  }

  @Override
  public String getHost() {
    return this.host;
  }

  @Override
  public int getPort() {
    return this.port;
  }

  @Override
  public String getAddress() {
    return String.format("%s:%d", this.host, this.port);
  }

  @Override
  public List<PartitionInfo> getPartitions() {
    return this.partitions;
  }

  @Override
  public String toString() {
    return "BrokerInfoImpl{"
        + "nodeId="
        + nodeId
        + ", host='"
        + host
        + '\''
        + ", port="
        + port
        + ", partitions="
        + partitions
        + '}';
  }

  class PartitionInfoImpl implements PartitionInfo {

    private final int partitionId;
    private final PartitionBrokerRole role;

    PartitionInfoImpl(final GatewayOuterClass.Partition partition) {
      this.partitionId = partition.getPartitionId();

      if (partition.getRole() == GatewayOuterClass.Partition.PartitionBrokerRole.LEADER) {
        this.role = PartitionBrokerRole.LEADER;
      } else if (partition.getRole() == GatewayOuterClass.Partition.PartitionBrokerRole.FOLLOWER) {
        this.role = PartitionBrokerRole.FOLLOWER;
      } else {
        throw new RuntimeException(
            String.format(
                "Unexpected partition broker role %s, should be one of %s",
                partition.getRole(), Arrays.toString(PartitionBrokerRole.values())));
      }
    }

    @Override
    public int getPartitionId() {
      return this.partitionId;
    }

    @Override
    public PartitionBrokerRole getRole() {
      return this.role;
    }

    @Override
    public boolean isLeader() {
      return this.role == PartitionBrokerRole.LEADER;
    }

    @Override
    public String toString() {
      return "PartitionInfoImpl{" + "partitionId=" + partitionId + ", role=" + role + '}';
    }
  }
}
