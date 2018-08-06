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
package io.zeebe.factories;

import static io.zeebe.protocol.Protocol.DEFAULT_TOPIC;

import io.zeebe.broker.client.api.commands.BrokerInfo;
import io.zeebe.broker.client.api.commands.PartitionInfo;
import io.zeebe.broker.client.api.commands.Topology;
import io.zeebe.broker.client.impl.clustering.BrokerInfoImpl;
import io.zeebe.broker.client.impl.clustering.PartitionInfoImpl;
import io.zeebe.broker.client.impl.clustering.TopologyImpl;
import java.util.ArrayList;

public class TopologyFactory implements TestFactory<Topology> {

  @Override
  public Topology getFixture() {
    final TopologyImpl topologyRequest = new TopologyImpl();
    final ArrayList<BrokerInfo> brokers = new ArrayList<BrokerInfo>();
    for (int i = 0; i < 10; i++) {

      final ArrayList<PartitionInfo> partitions = new ArrayList<PartitionInfo>();
      for (int j = 0; j < 3; j++) {
        final PartitionInfoImpl partition = new PartitionInfoImpl();
        partition.setState("LEADER");
        partition.setTopicName(DEFAULT_TOPIC);
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
}
