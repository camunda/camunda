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

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
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
 * Verifies that the purge completion check observes the purge through the status of the change it
 * planned, so that it also works against a cluster with more than one physical tenant - whose
 * {@code GET /actuator/cluster} response reports no cluster-wide change state.
 */
public class CamundaManagementClientPurgeTest {

  private static final String TOPOLOGY_PATH = "/actuator/cluster";
  private static final String PURGE_PATH = "/actuator/cluster/purge";

  private static final long CHANGE_ID = 7;
  private static final String CHANGE_PATH = "/actuator/cluster/changes/" + CHANGE_ID;

  /**
   * A topology of a cluster with more than one physical tenant: it reports the brokers, but neither
   * {@code lastChange} nor {@code pendingChange}, as that state is cluster-wide and has no single
   * physical tenant to be scoped to.
   */
  private static final String MULTI_PHYSICAL_TENANT_TOPOLOGY =
      "{"
          + brokers("{\"id\": 1, \"role\": \"leader\", \"state\": \"ACTIVE\"}")
          + ", \"physicalTenants\": ["
          + "  {\"id\": \"tenanta\", \"routing\": {\"version\": 1}},"
          + "  {\"id\": \"tenantb\", \"routing\": {\"version\": 1}}"
          + "]}";

  private static final String PURGING_TOPOLOGY =
      "{" + brokers("{\"id\": 1, \"role\": \"leader\", \"state\": \"BOOTSTRAPPING\"}") + "}";

  @RegisterExtension
  private static final WireMockExtension MANAGEMENT_API =
      WireMockExtension.newInstance()
          .options(WireMockConfiguration.wireMockConfig().dynamicPort())
          .build();

  @Test
  void shouldPurgeClusterWithMultiplePhysicalTenants() {
    // given
    stubPurge();
    stubChange(change("COMPLETED"));
    stubTopology(MULTI_PHYSICAL_TENANT_TOPOLOGY);

    // when
    assertThatCode(() -> createClient().purgeCluster(Duration.ofSeconds(10)))
        .doesNotThrowAnyException();

    // then
    MANAGEMENT_API.verify(getRequestedFor(urlPathEqualTo(CHANGE_PATH)));
  }

  @Test
  void shouldNotPurgeClusterWhileTheChangeIsInProgress() {
    // given
    stubPurge();
    stubChange(change("IN_PROGRESS"));
    stubTopology(MULTI_PHYSICAL_TENANT_TOPOLOGY);

    // when / then
    assertThatThrownBy(() -> createClient().purgeCluster(Duration.ofSeconds(1)))
        .hasMessage("Failed to purge the cluster, timeout expired.");
  }

  @Test
  void shouldNotPurgeClusterWhileAPartitionIsNotActiveYet() {
    // given
    stubPurge();
    stubChange(change("COMPLETED"));
    stubTopology(PURGING_TOPOLOGY);

    // when / then
    assertThatThrownBy(() -> createClient().purgeCluster(Duration.ofSeconds(1)))
        .hasMessage("Failed to purge the cluster, timeout expired.");
  }

  @Test
  void shouldNotPurgeClusterWhileTheChangeIsUnknownToTheCluster() {
    // given a broker that has not learned about the change yet
    stubPurge();
    MANAGEMENT_API.stubFor(get(urlPathEqualTo(CHANGE_PATH)).willReturn(notFound()));
    stubTopology(MULTI_PHYSICAL_TENANT_TOPOLOGY);

    // when / then
    assertThatThrownBy(() -> createClient().purgeCluster(Duration.ofSeconds(1)))
        .hasMessage("Failed to purge the cluster, timeout expired.");
  }

  @Test
  void shouldFailPurgeWhenTheClusterReportsTheChangeAsFailed() {
    // given
    stubPurge();
    stubChange(change("FAILED"));
    stubTopology(MULTI_PHYSICAL_TENANT_TOPOLOGY);

    // when / then the purge fails with the reason instead of waiting for the timeout to expire
    assertThatThrownBy(() -> createClient().purgeCluster(Duration.ofSeconds(10)))
        .hasMessage("Failed to purge the cluster.")
        .hasRootCauseInstanceOf(IllegalStateException.class)
        .hasStackTraceContaining("The cluster reported the purge as FAILED. [changeId: 7]");
  }

  @Test
  void shouldFailPurgeWhenTheClusterReportsTheChangeAsCancelled() {
    // given
    stubPurge();
    stubChange(change("CANCELLED"));
    stubTopology(MULTI_PHYSICAL_TENANT_TOPOLOGY);

    // when / then
    assertThatThrownBy(() -> createClient().purgeCluster(Duration.ofSeconds(10)))
        .hasMessage("Failed to purge the cluster.")
        .hasRootCauseInstanceOf(IllegalStateException.class)
        .hasStackTraceContaining("The cluster reported the purge as CANCELLED. [changeId: 7]");
  }

  private static CamundaManagementClient createClient() {
    return CamundaManagementClient.createClient(URI.create(MANAGEMENT_API.baseUrl()));
  }

  private static void stubPurge() {
    MANAGEMENT_API.stubFor(
        post(urlPathEqualTo(PURGE_PATH)).willReturn(okJson("{\"changeId\": " + CHANGE_ID + "}")));
  }

  private static void stubChange(final String change) {
    MANAGEMENT_API.stubFor(get(urlPathEqualTo(CHANGE_PATH)).willReturn(okJson(change)));
  }

  private static void stubTopology(final String topology) {
    MANAGEMENT_API.stubFor(get(urlPathEqualTo(TOPOLOGY_PATH)).willReturn(okJson(topology)));
  }

  private static String change(final String status) {
    return "{\"id\": "
        + CHANGE_ID
        + ", \"status\": \""
        + status
        + "\", \"startedAt\": \"2026-01-01T00:00:00Z\", \"completed\": [], \"pending\": []}";
  }

  private static String brokers(final String partition) {
    return "\"brokers\": [{\"id\": 0, \"state\": \"ACTIVE\", \"partitions\": [" + partition + "]}]";
  }
}
