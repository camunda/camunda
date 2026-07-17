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
import io.camunda.client.CredentialsProvider;
import io.camunda.client.spring.bean.CamundaClientRegistry;
import io.camunda.client.spring.properties.MultiCamundaClientProperties;
import io.camunda.client.spring.properties.MultiCamundaClientPropertiesResolver;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

/**
 * Verifies that each configured client derives its own {@link CredentialsProvider} from its merged
 * {@code auth} block, so one client cannot present another client's credentials — both at the
 * provider level and end-to-end over HTTP through the registered per-client beans.
 */
class PerClientCredentialsIsolationTest {

  @RegisterExtension
  static final WireMockExtension WM =
      WireMockExtension.newInstance().options(new WireMockConfiguration().dynamicPort()).build();

  private final CredentialsProviderConfiguration credentials =
      new CredentialsProviderConfiguration();

  private static String basicAuthHeader(final String username, final String password) {
    return "Basic "
        + Base64.getEncoder()
            .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
  }

  private static void sendTopology(final CamundaClient client) {
    try {
      client.newTopologyRequest().send().join();
    } catch (final Exception ignored) {
      // only the outgoing request (and its credentials) is under test; the response is irrelevant
    }
  }

  private static StandardEnvironment environmentWith(final Map<String, Object> properties) {
    final StandardEnvironment environment = new StandardEnvironment();
    environment.getPropertySources().addFirst(new MapPropertySource("test", properties));
    return environment;
  }

  private static Map<String, String> appliedHeaders(final CredentialsProvider provider)
      throws IOException {
    final Map<String, String> headers = new HashMap<>();
    provider.applyCredentials(headers::put);
    return headers;
  }

  @Test
  void shouldGiveEachClientItsOwnCredentials() throws IOException {
    // given two clients sharing the basic auth type but with distinct credentials
    final MultiCamundaClientProperties properties =
        MultiCamundaClientPropertiesResolver.resolve(
            environmentWith(
                Map.of(
                    "camunda.client.auth.method", "basic",
                    "camunda.clients.finance.auth.username", "financeuser",
                    "camunda.clients.finance.auth.password", "financepw",
                    "camunda.clients.risk.auth.username", "riskuser",
                    "camunda.clients.risk.auth.password", "riskpw")));

    // when each client's credentials are built from its own resolved auth block and applied
    final Map<String, String> financeHeaders =
        appliedHeaders(
            credentials.camundaClientCredentialsProvider(properties.getClients().get("finance")));
    final Map<String, String> riskHeaders =
        appliedHeaders(
            credentials.camundaClientCredentialsProvider(properties.getClients().get("risk")));

    // then each carries credentials, and finance cannot present risk's credentials (they differ)
    assertThat(financeHeaders).isNotEmpty();
    assertThat(riskHeaders).isNotEmpty();
    assertThat(financeHeaders).isNotEqualTo(riskHeaders);
  }

  @Test
  void shouldPresentEachClientsOwnCredentialsOverHttp() {
    // given two configured clients with distinct basic-auth credentials, pointed at WireMock
    WM.stubFor(WireMock.any(anyUrl()).willReturn(ok()));

    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CamundaAutoConfiguration.class))
        .withPropertyValues(
            "camunda.client.auth.method=basic",
            "camunda.client.rest-address=http://localhost:" + WM.getPort(),
            "camunda.client.prefer-rest-over-grpc=true",
            "camunda.clients.finance.auth.username=financeuser",
            "camunda.clients.finance.auth.password=financepw",
            "camunda.clients.finance.prefix-physical-tenant-path=false",
            "camunda.clients.risk.auth.username=riskuser",
            "camunda.clients.risk.auth.password=riskpw",
            "camunda.clients.risk.prefix-physical-tenant-path=false")
        .run(
            context -> {
              final CamundaClientRegistry registry = context.getBean(CamundaClientRegistry.class);

              // when each client issues a request
              sendTopology(registry.get("finance"));
              sendTopology(registry.get("risk"));

              // then WireMock received exactly the two clients' own credentials
              final Set<String> authHeaders =
                  WM.getAllServeEvents().stream()
                      .map(event -> event.getRequest().getHeader("Authorization"))
                      .filter(Objects::nonNull)
                      .collect(Collectors.toSet());
              assertThat(authHeaders)
                  .containsExactlyInAnyOrder(
                      basicAuthHeader("financeuser", "financepw"),
                      basicAuthHeader("riskuser", "riskpw"));
            });
  }
}
