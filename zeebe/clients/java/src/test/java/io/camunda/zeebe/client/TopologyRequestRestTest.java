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
package io.camunda.zeebe.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.camunda.zeebe.client.api.response.Topology;
import io.camunda.zeebe.client.impl.response.BrokerInfoImpl;
import io.camunda.zeebe.client.protocol.rest.BrokerInfo;
import io.camunda.zeebe.client.protocol.rest.Partition;
import io.camunda.zeebe.client.protocol.rest.Partition.HealthEnum;
import io.camunda.zeebe.client.protocol.rest.Partition.RoleEnum;
import io.camunda.zeebe.client.protocol.rest.TopologyResponse;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@WireMockTest
public final class TopologyRequestRestTest {
  private static final ObjectMapper JSON_MAPPER =
      new ObjectMapper().registerModules(new Jdk8Module());

  private ZeebeClient client;

  @BeforeEach
  void beforeEach(final WireMockRuntimeInfo mockInfo) throws URISyntaxException {
    client = createClient(mockInfo);
  }

  @AfterEach
  void afterEach() {
    if (client != null) {
      client.close();
    }
  }

  @Test
  void shouldRequestTopology(final WireMockRuntimeInfo mockInfo)
      throws JsonProcessingException, ExecutionException, InterruptedException {
    // given
    final WireMock mock = mockInfo.getWireMock();
    final TopologyResponse expected = createStubbedTopology();
    mock.register(
        WireMock.get("/v1/topology")
            .willReturn(WireMock.okJson(JSON_MAPPER.writeValueAsString(expected))));

    // when
    final Future<Topology> response = client.newTopologyRequest().send();

    // then
    assertThat(response).succeedsWithin(Duration.ofSeconds(5));
    final Topology topology = response.get();
    assertThat(topology.getGatewayVersion()).isEqualTo("1.0.0");
    assertThat(topology.getClusterSize()).isEqualTo(2);
    assertThat(topology.getReplicationFactor()).isEqualTo(2);
    assertThat(topology.getPartitionsCount()).isEqualTo(2);
    assertThat(topology.getBrokers())
        .containsExactlyInAnyOrderElementsOf(
            expected.getBrokers().stream().map(BrokerInfoImpl::new).collect(Collectors.toList()));
  }

  private TopologyResponse createStubbedTopology() {
    return new TopologyResponse()
        .gatewayVersion("1.0.0")
        .clusterSize(2)
        .replicationFactor(2)
        .partitionsCount(2)
        .addBrokersItem(
            new BrokerInfo()
                .version("1.0.1")
                .host("localhost")
                .port(1025)
                .nodeId(0)
                .addPartitionsItem(
                    new Partition().partitionId(1).health(HealthEnum.HEALTHY).role(RoleEnum.LEADER))
                .addPartitionsItem(
                    new Partition()
                        .partitionId(2)
                        .role(RoleEnum.FOLLOWER)
                        .health(HealthEnum.UNHEALTHY)))
        .addBrokersItem(
            new BrokerInfo()
                .version("1.0.3")
                .host("localhost")
                .port(1026)
                .nodeId(1)
                .addPartitionsItem(
                    new Partition()
                        .partitionId(1)
                        .health(HealthEnum.UNHEALTHY)
                        .role(RoleEnum.FOLLOWER))
                .addPartitionsItem(
                    new Partition()
                        .partitionId(2)
                        .role(RoleEnum.LEADER)
                        .health(HealthEnum.HEALTHY)));
  }

  private ZeebeClient createClient(final WireMockRuntimeInfo mockInfo) throws URISyntaxException {
    return ZeebeClient.newClientBuilder()
        .usePlaintext()
        .preferRestOverGrpc(true)
        .restAddress(new URI(mockInfo.getHttpBaseUrl()))
        .build();
  }
}
