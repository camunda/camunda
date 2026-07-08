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

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.net.URI;
import org.junit.jupiter.api.Test;

/**
 * Verifies how the REST client forms its base path per physical tenant: it auto-prefixes {@code
 * /physical-tenants/<id>} in front of {@code /v2} when a physical tenant id is set, unless
 * auto-prefixing is disabled. Only the outgoing request URL is asserted (via WireMock), so the
 * stubbed responses are irrelevant.
 */
@WireMockTest
final class PhysicalTenantRestPathTest {

  private static CamundaClient client(final WireMockRuntimeInfo mockInfo, final boolean append) {
    final CamundaClientBuilder builder =
        CamundaClient.newClientBuilder()
            .preferRestOverGrpc(true)
            .restAddress(URI.create(mockInfo.getHttpBaseUrl()))
            .physicalTenantId("riskproduction");
    if (!append) {
      builder.appendPhysicalTenantPath(false);
    }
    return builder.build();
  }

  private static void sendTopologyRequest(final CamundaClient client) {
    try {
      client.newTopologyRequest().send().join();
    } catch (final Exception ignored) {
      // only the outgoing request URL is under test; the (unstubbed) response is irrelevant
    }
  }

  @Test
  void shouldPrefixRestBasePathWithPhysicalTenant(final WireMockRuntimeInfo mockInfo) {
    // given
    try (final CamundaClient client = client(mockInfo, true)) {
      // when
      sendTopologyRequest(client);
    }

    // then
    verify(getRequestedFor(urlEqualTo("/physical-tenants/riskproduction/v2/topology")));
  }

  @Test
  void shouldUseVerbatimRestBasePathWhenAppendDisabled(final WireMockRuntimeInfo mockInfo) {
    // given
    try (final CamundaClient client = client(mockInfo, false)) {
      // when
      sendTopologyRequest(client);
    }

    // then
    verify(getRequestedFor(urlEqualTo("/v2/topology")));
  }

  @Test
  void shouldUseVerbatimRestBasePathWhenPhysicalTenantBlank(final WireMockRuntimeInfo mockInfo) {
    // given a blank physical tenant id, which must not produce a malformed /physical-tenants//v2
    try (final CamundaClient client =
        CamundaClient.newClientBuilder()
            .preferRestOverGrpc(true)
            .restAddress(URI.create(mockInfo.getHttpBaseUrl()))
            .physicalTenantId("   ")
            .build()) {
      // when
      sendTopologyRequest(client);
    }

    // then
    verify(getRequestedFor(urlEqualTo("/v2/topology")));
  }
}
