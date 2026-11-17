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
package io.camunda.client.spring.configuration;

import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.camunda.client.CamundaClient;
import io.camunda.client.spring.bean.CamundaClientRegistry;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Verifies the two REST base-URL modes per client: a client with a {@code physical-tenant-id} and
 * {@code prefix-physical-tenant-path=true} auto-prefixes its REST path with {@code
 * /physical-tenants/<id>}, while a client without prefixing sends bare {@code /v2} requests to its
 * configured {@code rest-address}. Both point at the same WireMock so the requested paths are
 * compared directly.
 */
class MultiClientRestBaseUrlTest {

  @RegisterExtension
  static final WireMockExtension WM =
      WireMockExtension.newInstance().options(new WireMockConfiguration().dynamicPort()).build();

  private static void sendTopology(final CamundaClient client) {
    try {
      client.newTopologyRequest().send().join();
    } catch (final Exception ignored) {
      // only the outgoing request path is under test; the response is irrelevant
    }
  }

  @Test
  void shouldAutoPrefixPhysicalTenantPathForPrefixedClientOnly() {
    // given a shared REST base pointing at WireMock, one auto-prefixing client and one bare client
    WM.stubFor(WireMock.any(anyUrl()).willReturn(ok()));

    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CamundaAutoConfiguration.class))
        .withPropertyValues(
            "camunda.client.rest-address=http://localhost:" + WM.getPort(),
            "camunda.client.prefer-rest-over-grpc=true",
            // finance targets its physical tenant via the auto-prefixed REST path
            "camunda.clients.finance.physical-tenant-id=finance",
            "camunda.clients.finance.prefix-physical-tenant-path=true",
            // risk uses the bare base URL verbatim (no physical-tenant path prefixing)
            "camunda.clients.risk.prefix-physical-tenant-path=false")
        .run(
            context -> {
              final CamundaClientRegistry registry = context.getBean(CamundaClientRegistry.class);

              // when each client issues a REST request
              sendTopology(registry.get("finance"));
              sendTopology(registry.get("risk"));

              // then finance is scoped under /physical-tenants/finance and risk is not
              final Set<String> paths =
                  WM.getAllServeEvents().stream()
                      .map(event -> event.getRequest().getUrl())
                      .filter(Objects::nonNull)
                      .collect(Collectors.toSet());
              assertThat(paths).contains("/physical-tenants/finance/v2/topology", "/v2/topology");
              assertThat(paths).noneMatch(path -> path.contains("physical-tenants/risk"));
            });
  }
}
