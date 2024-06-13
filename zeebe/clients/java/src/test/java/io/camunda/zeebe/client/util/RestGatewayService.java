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
package io.camunda.zeebe.client.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.camunda.zeebe.client.protocol.rest.BrokerInfo;
import io.camunda.zeebe.client.protocol.rest.Partition;
import io.camunda.zeebe.client.protocol.rest.Partition.HealthEnum;
import io.camunda.zeebe.client.protocol.rest.Partition.RoleEnum;
import io.camunda.zeebe.client.protocol.rest.TopologyResponse;
import java.util.Arrays;
import org.junit.jupiter.api.Assertions;

public class RestGatewayService {

  public static final ObjectMapper JSON_MAPPER = new ObjectMapper();

  private final WireMockRuntimeInfo mockInfo;

  public RestGatewayService(final WireMockRuntimeInfo mockInfo) {
    this.mockInfo = mockInfo;
  }

  public void onTopologyRequest(
      final int clusterSize,
      final int partitionsCount,
      final int replicationFactor,
      final String gatewayVersion,
      final BrokerInfo... brokers) {
    try {
      mockInfo
          .getWireMock()
          .register(
              WireMock.get("/v1/topology")
                  .willReturn(
                      WireMock.okJson(
                          JSON_MAPPER.writeValueAsString(
                              new TopologyResponse()
                                  .gatewayVersion(gatewayVersion)
                                  .clusterSize(clusterSize)
                                  .replicationFactor(replicationFactor)
                                  .partitionsCount(partitionsCount)
                                  .brokers(Arrays.asList(brokers))))));
    } catch (final JsonProcessingException e) {
      Assertions.fail("Couldn't register topology request", e);
    }
  }

  public static BrokerInfo broker(
      final int nodeId,
      final String host,
      final int port,
      final String version,
      final Partition... partitions) {
    return new BrokerInfo()
        .nodeId(nodeId)
        .host(host)
        .port(port)
        .version(version)
        .partitions(Arrays.asList(partitions));
  }

  public static Partition partition(
      final int partitionId, final RoleEnum role, final HealthEnum health) {
    return new Partition().partitionId(partitionId).role(role).health(health);
  }
}
