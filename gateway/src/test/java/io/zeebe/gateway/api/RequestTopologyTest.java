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
package io.zeebe.gateway.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.gateway.api.util.GatewayTest;
import io.zeebe.gateway.protocol.GatewayOuterClass.BrokerInfo;
import io.zeebe.gateway.protocol.GatewayOuterClass.Partition;
import io.zeebe.gateway.protocol.GatewayOuterClass.Partition.PartitionBrokerRole;
import io.zeebe.gateway.protocol.GatewayOuterClass.TopologyRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.TopologyResponse;
import org.junit.Test;

public class RequestTopologyTest extends GatewayTest {

  @Test
  public void shouldMapRequestAndResponse() {
    // given
    final RequestTopologyStub stub = new RequestTopologyStub();
    stub.registerWith(gateway);

    final BrokerInfo broker1 =
        BrokerInfo.newBuilder()
            .setNodeId(0)
            .setHost("foo")
            .setPort(1234)
            .addPartitions(
                Partition.newBuilder().setPartitionId(0).setRole(PartitionBrokerRole.LEADER))
            .addPartitions(
                Partition.newBuilder().setPartitionId(1).setRole(PartitionBrokerRole.FOLLOWER))
            .build();

    final BrokerInfo broker2 =
        BrokerInfo.newBuilder()
            .setNodeId(1)
            .setHost("bar")
            .setPort(5678)
            .addPartitions(
                Partition.newBuilder().setPartitionId(0).setRole(PartitionBrokerRole.FOLLOWER))
            .addPartitions(
                Partition.newBuilder().setPartitionId(1).setRole(PartitionBrokerRole.LEADER))
            .build();

    // when
    final TopologyResponse response = client.topology(TopologyRequest.getDefaultInstance());

    // then
    assertThat(response.getClusterSize()).isEqualTo(stub.getClusterSize());
    assertThat(response.getPartitionsCount()).isEqualTo(stub.getPartitionsCount());
    assertThat(response.getReplicationFactor()).isEqualTo(stub.getReplicationFactor());
    assertThat(response.getBrokersList()).containsExactlyInAnyOrder(broker1, broker2);
  }
}
