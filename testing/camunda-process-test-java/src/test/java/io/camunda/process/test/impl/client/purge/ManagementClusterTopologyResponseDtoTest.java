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
import io.camunda.process.test.impl.client.purge.ManagementClusterTopologyResponseDto.ChangeCompletion;
import java.io.IOException;
import org.junit.jupiter.api.Test;

/**
 * Verifies how a {@code GET /actuator/cluster} response is read for the completion of a topology
 * change, over the two shapes the endpoint answers with: a response covering a single physical
 * tenant, which carries the cluster-wide {@code lastChange}, and one covering more than one
 * physical tenant, which does not.
 */
public class ManagementClusterTopologyResponseDtoTest {

  private static final long CHANGE_ID = 7;

  private static final String HEALTHY_BROKERS =
      "\"brokers\": ["
          + "  {"
          + "    \"id\": 0,"
          + "    \"state\": \"ACTIVE\","
          + "    \"partitions\": [{\"id\": 1, \"role\": \"leader\", \"state\": \"ACTIVE\"}]"
          + "  }"
          + "]";

  private static final String PURGING_BROKERS =
      "\"brokers\": ["
          + "  {"
          + "    \"id\": 0,"
          + "    \"state\": \"ACTIVE\","
          + "    \"partitions\": [{\"id\": 1, \"role\": \"leader\", \"state\": \"BOOTSTRAPPING\"}]"
          + "  }"
          + "]";

  private static final String PENDING_CHANGE =
      "\"pendingChange\": {\"id\": 7, \"status\": \"IN_PROGRESS\", \"pending\": []}";

  private final ObjectMapper objectMapper =
      new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

  @Test
  void shouldReportCompletedWhenTheChangeIsTheLastCompletedOne() throws IOException {
    // given
    final ManagementClusterTopologyResponseDto topology =
        parse("{" + HEALTHY_BROKERS + ", " + lastChange(CHANGE_ID) + "}");

    // when
    final ChangeCompletion completion = topology.getChangeCompletion(CHANGE_ID);

    // then
    assertThat(completion).isEqualTo(ChangeCompletion.COMPLETED);
  }

  @Test
  void shouldReportCompletedWhenAnotherChangeCompletedAfterIt() throws IOException {
    // given
    final ManagementClusterTopologyResponseDto topology =
        parse("{" + HEALTHY_BROKERS + ", " + lastChange(CHANGE_ID + 1) + "}");

    // when
    final ChangeCompletion completion = topology.getChangeCompletion(CHANGE_ID);

    // then
    assertThat(completion).isEqualTo(ChangeCompletion.COMPLETED);
  }

  @Test
  void shouldReportNotCompletedWhenAnEarlierChangeIsTheLastCompletedOne() throws IOException {
    // given
    final ManagementClusterTopologyResponseDto topology =
        parse("{" + HEALTHY_BROKERS + ", " + lastChange(CHANGE_ID - 1) + "}");

    // when
    final ChangeCompletion completion = topology.getChangeCompletion(CHANGE_ID);

    // then
    assertThat(completion).isEqualTo(ChangeCompletion.NOT_COMPLETED);
  }

  @Test
  void shouldReportNotCompletedWhileAChangeIsStillPending() throws IOException {
    // given
    final ManagementClusterTopologyResponseDto topology =
        parse("{" + HEALTHY_BROKERS + ", " + lastChange(CHANGE_ID) + ", " + PENDING_CHANGE + "}");

    // when
    final ChangeCompletion completion = topology.getChangeCompletion(CHANGE_ID);

    // then
    assertThat(completion).isEqualTo(ChangeCompletion.NOT_COMPLETED);
  }

  @Test
  void shouldReportNotCompletedWhileAPartitionIsNotActiveYet() throws IOException {
    // given
    final ManagementClusterTopologyResponseDto topology =
        parse("{" + PURGING_BROKERS + ", " + lastChange(CHANGE_ID) + "}");

    // when
    final ChangeCompletion completion = topology.getChangeCompletion(CHANGE_ID);

    // then
    assertThat(completion).isEqualTo(ChangeCompletion.NOT_COMPLETED);
  }

  @Test
  void shouldReportNotCompletedWhenNoChangeHasCompletedYet() throws IOException {
    // given a single-tenant cluster that never completed a topology change
    final ManagementClusterTopologyResponseDto topology = parse("{" + HEALTHY_BROKERS + "}");

    // when
    final ChangeCompletion completion = topology.getChangeCompletion(CHANGE_ID);

    // then
    assertThat(completion).isEqualTo(ChangeCompletion.NOT_COMPLETED);
  }

  @Test
  void shouldReportNotCompletedWhenAResponseScopedToOnePhysicalTenantHasNoLastChange()
      throws IOException {
    // given
    final ManagementClusterTopologyResponseDto topology =
        parse("{" + HEALTHY_BROKERS + ", " + physicalTenants("tenant-a") + "}");

    // when
    final ChangeCompletion completion = topology.getChangeCompletion(CHANGE_ID);

    // then
    assertThat(completion).isEqualTo(ChangeCompletion.NOT_COMPLETED);
  }

  @Test
  void shouldReportCompletedWhenAResponseScopedToOnePhysicalTenantHasLastChange()
      throws IOException {
    // given
    final ManagementClusterTopologyResponseDto topology =
        parse(
            "{"
                + HEALTHY_BROKERS
                + ", "
                + lastChange(CHANGE_ID)
                + ", "
                + physicalTenants("tenant-a")
                + "}");

    // when
    final ChangeCompletion completion = topology.getChangeCompletion(CHANGE_ID);

    // then
    assertThat(completion).isEqualTo(ChangeCompletion.COMPLETED);
  }

  @Test
  void shouldReportNotReportedWhenTheResponseCoversMultiplePhysicalTenants() throws IOException {
    // given a response that omits lastChange because it covers more than one physical tenant
    final ManagementClusterTopologyResponseDto topology =
        parse("{" + HEALTHY_BROKERS + ", " + physicalTenants("tenant-a", "tenant-b") + "}");

    // when
    final ChangeCompletion completion = topology.getChangeCompletion(CHANGE_ID);

    // then
    assertThat(completion).isEqualTo(ChangeCompletion.NOT_REPORTED);
  }

  @Test
  void shouldReadAPhysicalTenantToScopeTo() throws IOException {
    // given
    final ManagementClusterTopologyResponseDto topology =
        parse("{" + HEALTHY_BROKERS + ", " + physicalTenants("tenant-a", "tenant-b") + "}");

    // when
    final String physicalTenantId = topology.getFirstPhysicalTenantId();

    // then
    assertThat(physicalTenantId).isEqualTo("tenant-a");
  }

  @Test
  void shouldReadNoPhysicalTenantToScopeToWhenTheResponseNamesNone() throws IOException {
    // given
    final ManagementClusterTopologyResponseDto topology =
        parse("{" + HEALTHY_BROKERS + ", \"physicalTenants\": [{}, {}]}");

    // when
    final String physicalTenantId = topology.getFirstPhysicalTenantId();

    // then
    assertThat(physicalTenantId).isNull();
  }

  @Test
  void shouldReadNoPhysicalTenantToScopeToFromASingleTenantResponse() throws IOException {
    // given
    final ManagementClusterTopologyResponseDto topology =
        parse("{" + HEALTHY_BROKERS + ", " + lastChange(CHANGE_ID) + "}");

    // when
    final String physicalTenantId = topology.getFirstPhysicalTenantId();

    // then
    assertThat(physicalTenantId).isNull();
  }

  private static String lastChange(final long changeId) {
    return "\"lastChange\": {"
        + "\"id\": "
        + changeId
        + ", \"status\": \"COMPLETED\","
        + "\"startedAt\": \"2026-01-01T00:00:00Z\", \"completedAt\": \"2026-01-01T00:00:01Z\""
        + "}";
  }

  private static String physicalTenants(final String... physicalTenantIds) {
    final StringBuilder physicalTenants = new StringBuilder("\"physicalTenants\": [");
    for (int i = 0; i < physicalTenantIds.length; i++) {
      if (i > 0) {
        physicalTenants.append(", ");
      }
      physicalTenants
          .append("{\"id\": \"")
          .append(physicalTenantIds[i])
          .append("\", \"routing\": {\"version\": 1}}");
    }
    return physicalTenants.append("]").toString();
  }

  private ManagementClusterTopologyResponseDto parse(final String response) throws IOException {
    return objectMapper.readValue(response, ManagementClusterTopologyResponseDto.class);
  }
}
