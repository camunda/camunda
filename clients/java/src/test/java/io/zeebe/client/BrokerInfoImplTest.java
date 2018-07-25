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
package io.zeebe.client;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import io.zeebe.client.api.commands.PartitionBrokerRole;
import io.zeebe.client.api.commands.PartitionInfo;
import io.zeebe.client.factories.GrpcBrokerInfoFactory;
import io.zeebe.client.impl.BrokerInfoImpl;
import io.zeebe.gateway.protocol.GatewayOuterClass;
import io.zeebe.gateway.protocol.GatewayOuterClass.Partition;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class BrokerInfoImplTest {

  private GatewayOuterClass.BrokerInfo broker;

  @Before
  public void setUp() {
    final GrpcBrokerInfoFactory factory = new GrpcBrokerInfoFactory();
    this.broker = factory.getFixture();
  }

  @Test
  public void shouldCreateCorrectObject() {
    final BrokerInfoImpl impl = new BrokerInfoImpl(broker);

    assertThat(impl.getHost()).isEqualTo(broker.getHost());
    assertThat(impl.getPort()).isEqualTo(broker.getPort());
    assertThat(impl.getAddress())
        .isEqualTo(String.format("%s:%d", broker.getHost(), broker.getPort()));

    assertThat(impl.getPartitions().size()).isEqualTo(1);

    final List<PartitionInfo> receivedPartitions = impl.getPartitions();
    final List<Partition> expectedPartitions = broker.getPartitionsList();
    for (int i = 0; i < broker.getPartitionsCount(); ++i) {
      final PartitionInfo received = receivedPartitions.get(i);
      final Partition expected = expectedPartitions.get(i);

      assertThat(received.getTopicName()).isEqualTo(expected.getTopicName());
      assertThat(received.getRole() == PartitionBrokerRole.LEADER)
          .isEqualTo(expected.getRole() == GatewayOuterClass.Partition.PartitionBrokerRole.LEADER);
      assertThat(received.getPartitionId()).isEqualTo(expected.getPartitionId());
      assertThat(received.isLeader())
          .isEqualTo(expected.getRole() == GatewayOuterClass.Partition.PartitionBrokerRole.LEADER);
    }
  }
}
