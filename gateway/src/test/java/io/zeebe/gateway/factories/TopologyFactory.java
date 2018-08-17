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
package io.zeebe.gateway.factories;

import io.zeebe.gateway.api.commands.BrokerInfo;
import io.zeebe.gateway.api.commands.PartitionInfo;
import io.zeebe.gateway.api.commands.Topology;
import io.zeebe.gateway.impl.clustering.BrokerInfoImpl;
import io.zeebe.gateway.impl.clustering.PartitionInfoImpl;
import io.zeebe.gateway.impl.clustering.TopologyImpl;
import io.zeebe.gateway.protocol.GatewayOuterClass;
import java.util.ArrayList;
import java.util.List;

public class TopologyFactory implements TestFactory<Topology> {

  private final ArrayList<BrokerInfo> brokers = new ArrayList<>();

  @Override
  public Topology getFixture() {
    final TopologyImpl topologyRequest = new TopologyImpl();
    for (int i = 0; i < 10; i++) {

      final ArrayList<PartitionInfo> partitions = new ArrayList<PartitionInfo>();
      for (int j = 0; j < 3; j++) {
        final PartitionInfoImpl partition = new PartitionInfoImpl();
        partition.setState("LEADER");
        partition.setPartitionId(j);
        partitions.add(partition);
      }

      final BrokerInfoImpl brokerInfo = new BrokerInfoImpl();
      brokerInfo.setHost("localhost");
      brokerInfo.setPort(50015);
      brokerInfo.setPartitions(partitions);

      brokers.add(brokerInfo);
    }

    topologyRequest.setBrokers(brokers);
    return topologyRequest;
  }

  private boolean comparePartitions(
      final GatewayOuterClass.Partition grpcPartition, final PartitionInfo partition) {
    return grpcPartition.getPartitionId() == partition.getPartitionId();
  }

  private boolean compareBrokers(
      final GatewayOuterClass.BrokerInfo grpcBroker, final BrokerInfo broker) {
    for (final GatewayOuterClass.Partition grpcPartition : grpcBroker.getPartitionsList()) {
      if (broker
          .getPartitions()
          .stream()
          .noneMatch(partition -> comparePartitions(grpcPartition, partition))) {
        return false;
      }
    }
    return grpcBroker.getHost().equals(broker.getHost())
        && grpcBroker.getPort() == broker.getPort();
  }

  public boolean containsBroker(final GatewayOuterClass.BrokerInfo grpcBroker) {
    return brokers.stream().anyMatch(broker -> compareBrokers(grpcBroker, broker));
  }

  public List<BrokerInfo> getBrokersList() {
    return brokers;
  }
}
