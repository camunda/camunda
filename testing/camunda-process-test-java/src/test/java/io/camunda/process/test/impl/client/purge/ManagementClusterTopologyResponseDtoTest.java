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
package io.camunda.process.test.impl.client.purge;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.junit.jupiter.api.Test;

/**
 * Verifies how a {@code GET /actuator/cluster} response is read for the health of the cluster, over
 * both shapes the endpoint answers with: a response covering a single physical tenant and one
 * covering more than one, which omits the cluster-wide change state but reports the same brokers.
 */
public class ManagementClusterTopologyResponseDtoTest {

  private static final String ACTIVE_PARTITION =
      "{\"id\": 1, \"role\": \"leader\", \"state\": \"ACTIVE\"}";
  private static final String BOOTSTRAPPING_PARTITION =
      "{\"id\": 2, \"role\": \"leader\", \"state\": \"BOOTSTRAPPING\"}";

  private final ObjectMapper objectMapper =
      new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

  @Test
  void shouldReportHealthyWhenEveryPartitionIsActive() throws IOException {
    // given a response of a cluster with a single physical tenant
    final ManagementClusterTopologyResponseDto topology =
        parse(
            "{"
                + brokers(ACTIVE_PARTITION + ", " + ACTIVE_PARTITION)
                + ", \"version\": 3, "
                + "\"lastChange\": {\"id\": 7, \"status\": \"COMPLETED\"}}");

    // when
    final boolean healthy = topology.isClusterHealthy();

    // then
    assertThat(healthy).isTrue();
  }

  @Test
  void shouldReportHealthyWhenTheResponseCoversMultiplePhysicalTenants() throws IOException {
    // given a response that omits the cluster-wide change state because it covers more than one
    // physical tenant
    final ManagementClusterTopologyResponseDto topology =
        parse(
            "{"
                + brokers(ACTIVE_PARTITION + ", " + ACTIVE_PARTITION)
                + ", \"physicalTenants\": ["
                + "  {\"id\": \"tenanta\", \"routing\": {\"version\": 1}},"
                + "  {\"id\": \"tenantb\", \"routing\": {\"version\": 1}}"
                + "]}");

    // when
    final boolean healthy = topology.isClusterHealthy();

    // then
    assertThat(healthy).isTrue();
  }

  @Test
  void shouldReportUnhealthyWhileAPartitionIsNotActiveYet() throws IOException {
    // given
    final ManagementClusterTopologyResponseDto topology =
        parse("{" + brokers(ACTIVE_PARTITION + ", " + BOOTSTRAPPING_PARTITION) + "}");

    // when
    final boolean healthy = topology.isClusterHealthy();

    // then
    assertThat(healthy).isFalse();
  }

  @Test
  void shouldReportUnhealthyWhenTheResponseReportsNoBroker() throws IOException {
    // given
    final ManagementClusterTopologyResponseDto topology = parse("{\"version\": 0}");

    // when
    final boolean healthy = topology.isClusterHealthy();

    // then
    assertThat(healthy).isFalse();
  }

  private static String brokers(final String partitions) {
    return "\"brokers\": [{\"id\": 0, \"state\": \"ACTIVE\", \"partitions\": ["
        + partitions
        + "]}]";
  }

  private ManagementClusterTopologyResponseDto parse(final String response) throws IOException {
    return objectMapper.readValue(response, ManagementClusterTopologyResponseDto.class);
  }
}
