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
package io.zeebe.gateway;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import io.zeebe.gateway.api.commands.BrokerInfo;
import io.zeebe.gateway.api.commands.PartitionInfo;
import io.zeebe.gateway.api.commands.Topology;
import io.zeebe.gateway.factories.TopologyFactory;
import io.zeebe.gateway.protocol.GatewayOuterClass.HealthResponse;
import java.util.LinkedList;
import org.junit.Test;

public class ResponseMapperTest {

  @Test
  public void shouldTestHealthCheckMapping() {
    final ResponseMapper responseMapper = new ResponseMapper();
    final Topology topology = new TopologyFactory().getFixture();

    final HealthResponse response = responseMapper.toResponse(topology);
    final LinkedList<BrokerInfo> expectedBrokers = new LinkedList<>(topology.getBrokers());
    assertThat(response.getBrokersCount()).isEqualTo(expectedBrokers.size());

    topology
        .getBrokers()
        .forEach(
            received -> {
              final BrokerInfo expected = expectedBrokers.pop();

              assertThat(expected.getHost()).isEqualTo(received.getHost());
              assertThat(expected.getPort()).isEqualTo(received.getPort());
              assertThat(expected.getAddress())
                  .isEqualTo(String.format("%s:%d", received.getHost(), received.getPort()));

              final LinkedList<PartitionInfo> expectedPartitions =
                  new LinkedList<>(expected.getPartitions());

              received
                  .getPartitions()
                  .forEach(
                      receivedPartition -> {
                        final PartitionInfo expectedPartition = expectedPartitions.pop();
                        assertThat(expectedPartition.getPartitionId())
                            .isEqualTo(receivedPartition.getPartitionId());

                        assertThat(expectedPartition.getRole().toString())
                            .isEqualTo(receivedPartition.getRole().toString());

                        assertThat(expectedPartition.getTopicName())
                            .isEqualTo(receivedPartition.getTopicName());
                      });
            });
  }
}
