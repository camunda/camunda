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
package io.camunda.process.test.impl.client;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Verifies that the purge completion check observes the purge on both shapes of the {@code GET
 * /actuator/cluster} response: a response covering a single physical tenant, which carries the
 * cluster-wide {@code lastChange}, and one covering more than one physical tenant, which omits it
 * and therefore has to be re-read scoped to a physical tenant.
 */
public class CamundaManagementClientPurgeTest {

  private static final String TOPOLOGY_PATH = "/actuator/cluster";
  private static final String PURGE_PATH = "/actuator/cluster/purge";
  private static final String PHYSICAL_TENANT_PARAMETER = "physicalTenant";

  private static final long CHANGE_ID = 7;

  private static final String HEALTHY_BROKERS =
      "\"brokers\": ["
          + "  {"
          + "    \"id\": 0,"
          + "    \"state\": \"ACTIVE\","
          + "    \"partitions\": [{\"id\": 1, \"role\": \"leader\", \"state\": \"ACTIVE\"}]"
          + "  }"
          + "]";

  /** A response covering more than one physical tenant, which reports no {@code lastChange}. */
  private static final String MULTI_PHYSICAL_TENANT_TOPOLOGY =
      "{"
          + HEALTHY_BROKERS
          + ", \"physicalTenants\": ["
          + "  {\"id\": \"tenant-a\", \"routing\": {\"version\": 1}},"
          + "  {\"id\": \"tenant-b\", \"routing\": {\"version\": 1}}"
          + "]}";

  /** The same response, but naming no physical tenant to scope a follow-up request to. */
  private static final String UNNAMED_PHYSICAL_TENANTS_TOPOLOGY =
      "{" + HEALTHY_BROKERS + ", \"physicalTenants\": [{}, {}]}";

  @RegisterExtension
  private static final WireMockExtension MANAGEMENT_API =
      WireMockExtension.newInstance()
          .options(WireMockConfiguration.wireMockConfig().dynamicPort())
          .build();

  @Test
  void shouldPurgeClusterWithMultiplePhysicalTenants() {
    // given
    stubPurge();
    stubUnscopedTopology(MULTI_PHYSICAL_TENANT_TOPOLOGY);
    stubScopedTopology("tenant-a", scopedTopology(CHANGE_ID));

    // when
    assertThatCode(() -> createClient().purgeCluster(Duration.ofSeconds(10)))
        .doesNotThrowAnyException();

    // then
    MANAGEMENT_API.verify(
        getRequestedFor(urlPathEqualTo(TOPOLOGY_PATH))
            .withQueryParam(PHYSICAL_TENANT_PARAMETER, equalTo("tenant-a")));
  }

  @Test
  void shouldNotPurgeClusterWhileThePhysicalTenantReportsAnEarlierChange() {
    // given
    stubPurge();
    stubUnscopedTopology(MULTI_PHYSICAL_TENANT_TOPOLOGY);
    stubScopedTopology("tenant-a", scopedTopology(CHANGE_ID - 1));

    // when
    assertThatThrownBy(() -> createClient().purgeCluster(Duration.ofSeconds(1)))
        .hasMessage("Failed to purge the cluster, timeout expired.");

    // then the check kept polling the physical tenant instead of reporting a purge that never
    // completed
    MANAGEMENT_API.verify(
        getRequestedFor(urlPathEqualTo(TOPOLOGY_PATH))
            .withQueryParam(PHYSICAL_TENANT_PARAMETER, equalTo("tenant-a")));
  }

  @Test
  void shouldFailPurgeWhenTheTopologyNamesNoPhysicalTenant() {
    // given
    stubPurge();
    stubUnscopedTopology(UNNAMED_PHYSICAL_TENANTS_TOPOLOGY);

    // when / then the purge fails with the reason instead of waiting for the timeout to expire
    assertThatThrownBy(() -> createClient().purgeCluster(Duration.ofSeconds(10)))
        .hasMessage("Failed to purge the cluster.")
        .hasRootCauseInstanceOf(IllegalStateException.class)
        .hasStackTraceContaining("but it named none");
  }

  @Test
  void shouldPurgeClusterWithASinglePhysicalTenantWithoutScoping() {
    // given
    stubPurge();
    stubUnscopedTopology(singlePhysicalTenantTopology(CHANGE_ID));

    // when
    assertThatCode(() -> createClient().purgeCluster(Duration.ofSeconds(10)))
        .doesNotThrowAnyException();

    // then a cluster that predates physical tenants is not asked about any of them
    MANAGEMENT_API.verify(
        0,
        getRequestedFor(urlPathEqualTo(TOPOLOGY_PATH))
            .withQueryParam(PHYSICAL_TENANT_PARAMETER, matching(".*")));
  }

  private static CamundaManagementClient createClient() {
    return CamundaManagementClient.createClient(URI.create(MANAGEMENT_API.baseUrl()));
  }

  private static void stubPurge() {
    MANAGEMENT_API.stubFor(
        post(urlPathEqualTo(PURGE_PATH)).willReturn(okJson("{\"changeId\": " + CHANGE_ID + "}")));
  }

  private static void stubUnscopedTopology(final String topology) {
    MANAGEMENT_API.stubFor(
        get(urlPathEqualTo(TOPOLOGY_PATH))
            .withQueryParam(PHYSICAL_TENANT_PARAMETER, absent())
            .willReturn(okJson(topology)));
  }

  private static void stubScopedTopology(final String physicalTenantId, final String topology) {
    MANAGEMENT_API.stubFor(
        get(urlPathEqualTo(TOPOLOGY_PATH))
            .withQueryParam(PHYSICAL_TENANT_PARAMETER, equalTo(physicalTenantId))
            .willReturn(okJson(topology)));
  }

  /** A response scoped to one physical tenant, which does carry the cluster-wide lastChange. */
  private static String scopedTopology(final long lastChangeId) {
    return "{"
        + HEALTHY_BROKERS
        + ", "
        + lastChange(lastChangeId)
        + ", \"physicalTenants\": [{\"id\": \"tenant-a\", \"routing\": {\"version\": 1}}]}";
  }

  /** A response of a cluster that has a single physical tenant, which predates physical tenants. */
  private static String singlePhysicalTenantTopology(final long lastChangeId) {
    return "{" + HEALTHY_BROKERS + ", " + lastChange(lastChangeId) + "}";
  }

  private static String lastChange(final long changeId) {
    return "\"lastChange\": {\"id\": " + changeId + ", \"status\": \"COMPLETED\"}";
  }
}
