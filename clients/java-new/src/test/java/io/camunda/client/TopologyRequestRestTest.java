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
package io.camunda.client;

import static io.camunda.client.protocol.rest.Partition.HealthEnum.DEAD;
import static io.camunda.client.protocol.rest.Partition.HealthEnum.HEALTHY;
import static io.camunda.client.protocol.rest.Partition.HealthEnum.UNHEALTHY;
import static io.camunda.client.protocol.rest.Partition.RoleEnum.FOLLOWER;
import static io.camunda.client.protocol.rest.Partition.RoleEnum.INACTIVE;
import static io.camunda.client.protocol.rest.Partition.RoleEnum.LEADER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.response.PartitionBrokerHealth;
import io.camunda.client.api.response.PartitionBrokerRole;
import io.camunda.client.api.response.PartitionInfo;
import io.camunda.client.api.response.Topology;
import io.camunda.client.protocol.rest.BrokerInfo;
import io.camunda.client.protocol.rest.Partition;
import io.camunda.client.protocol.rest.ProblemDetail;
import io.camunda.client.protocol.rest.TopologyResponse;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayPaths;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

public final class TopologyRequestRestTest extends ClientRestTest {

  @Test
  void shouldRequestTopology() throws ExecutionException, InterruptedException {
    // given
    gatewayService.onTopologyRequest(
        new TopologyResponse()
            .gatewayVersion("1.22.3-SNAPSHOT")
            .clusterSize(3)
            .replicationFactor(3)
            .partitionsCount(2)
            .addBrokersItem(
                new BrokerInfo()
                    .nodeId(0)
                    .host("host1")
                    .port(123)
                    .version("1.22.3-SNAPSHOT")
                    .partitions(
                        Arrays.asList(
                            new Partition().partitionId(0).role(LEADER).health(HEALTHY),
                            new Partition().partitionId(1).role(FOLLOWER).health(UNHEALTHY))))
            .addBrokersItem(
                new BrokerInfo()
                    .nodeId(1)
                    .host("host2")
                    .port(212)
                    .version("2.22.3-SNAPSHOT")
                    .partitions(
                        Arrays.asList(
                            new Partition().partitionId(0).role(FOLLOWER).health(HEALTHY),
                            new Partition().partitionId(1).role(LEADER).health(HEALTHY))))
            .addBrokersItem(
                new BrokerInfo()
                    .nodeId(2)
                    .host("host3")
                    .port(432)
                    .version("3.22.3-SNAPSHOT")
                    .partitions(
                        Arrays.asList(
                            new Partition().partitionId(0).role(FOLLOWER).health(UNHEALTHY),
                            new Partition().partitionId(1).role(INACTIVE).health(UNHEALTHY)))));

    // when
    final Future<Topology> response = client.newTopologyRequest().send();

    // then
    assertThat(response).succeedsWithin(Duration.ofSeconds(5));
    final Topology topology = response.get();
    assertThat(topology.getClusterSize()).isEqualTo(3);
    assertThat(topology.getPartitionsCount()).isEqualTo(2);
    assertThat(topology.getReplicationFactor()).isEqualTo(3);
    assertThat(topology.getGatewayVersion()).isEqualTo("1.22.3-SNAPSHOT");

    final List<io.camunda.client.api.response.BrokerInfo> brokers = topology.getBrokers();
    assertThat(brokers).hasSize(3);

    io.camunda.client.api.response.BrokerInfo broker = brokers.get(0);
    assertThat(broker.getNodeId()).isEqualTo(0);
    assertThat(broker.getHost()).isEqualTo("host1");
    assertThat(broker.getPort()).isEqualTo(123);
    assertThat(broker.getAddress()).isEqualTo("host1:123");
    assertThat(broker.getVersion()).isEqualTo("1.22.3-SNAPSHOT");
    assertThat(broker.getPartitions())
        .extracting(PartitionInfo::getPartitionId, PartitionInfo::getRole, PartitionInfo::getHealth)
        .containsOnly(
            tuple(0, PartitionBrokerRole.LEADER, PartitionBrokerHealth.HEALTHY),
            tuple(1, PartitionBrokerRole.FOLLOWER, PartitionBrokerHealth.UNHEALTHY));

    broker = brokers.get(1);
    assertThat(broker.getNodeId()).isEqualTo(1);
    assertThat(broker.getHost()).isEqualTo("host2");
    assertThat(broker.getPort()).isEqualTo(212);
    assertThat(broker.getAddress()).isEqualTo("host2:212");
    assertThat(broker.getVersion()).isEqualTo("2.22.3-SNAPSHOT");
    assertThat(broker.getPartitions())
        .extracting(PartitionInfo::getPartitionId, PartitionInfo::getRole, PartitionInfo::getHealth)
        .containsOnly(
            tuple(0, PartitionBrokerRole.FOLLOWER, PartitionBrokerHealth.HEALTHY),
            tuple(1, PartitionBrokerRole.LEADER, PartitionBrokerHealth.HEALTHY));

    broker = brokers.get(2);
    assertThat(broker.getNodeId()).isEqualTo(2);
    assertThat(broker.getHost()).isEqualTo("host3");
    assertThat(broker.getPort()).isEqualTo(432);
    assertThat(broker.getAddress()).isEqualTo("host3:432");
    assertThat(broker.getVersion()).isEqualTo("3.22.3-SNAPSHOT");
    assertThat(broker.getPartitions())
        .extracting(PartitionInfo::getPartitionId, PartitionInfo::getRole, PartitionInfo::getHealth)
        .containsOnly(
            tuple(0, PartitionBrokerRole.FOLLOWER, PartitionBrokerHealth.UNHEALTHY),
            tuple(1, PartitionBrokerRole.INACTIVE, PartitionBrokerHealth.UNHEALTHY));
  }

  @Test
  void shouldAcceptDeadPartitions() {
    // given
    gatewayService.onTopologyRequest(
        new TopologyResponse()
            .gatewayVersion("1.22.3-SNAPSHOT")
            .clusterSize(1)
            .replicationFactor(1)
            .partitionsCount(1)
            .addBrokersItem(
                new BrokerInfo()
                    .nodeId(0)
                    .host("host1")
                    .port(123)
                    .version("1.22.3-SNAPSHOT")
                    .addPartitionsItem(new Partition().partitionId(0).role(LEADER).health(DEAD))));

    // when
    final Topology topology = client.newTopologyRequest().send().join();

    // then
    assertThat(topology.getBrokers().get(0).getPartitions().get(0))
        .extracting(PartitionInfo::getHealth)
        .isEqualTo(PartitionBrokerHealth.DEAD);
  }

  @Test
  public void shouldRaiseExceptionOnError() {
    // given
    gatewayService.errorOnRequest(
        RestGatewayPaths.getTopologyUrl(),
        () -> new ProblemDetail().title("Invalid request").status(400));

    // when
    assertThatThrownBy(() -> client.newTopologyRequest().send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Invalid request");
  }
}
