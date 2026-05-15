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
package io.camunda.client.spring.secret;

import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.impl.CamundaObjectMapper;
import io.camunda.client.impl.secret.SecretResolvingJsonMapper;
import java.net.URI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Exercises the wire contract between {@link HttpSecretsClient} and the gateway's {@code
 * /v2/secrets/resolve} endpoint, plus the integration with {@link SecretResolvingJsonMapper}.
 *
 * <p>WireMock stands in for the gateway and asserts the exact JSON shape posted by the client; the
 * mapper is then used to deserialize a variable payload to verify that the resolved value is
 * spliced back transparently.
 */
class SecretResolutionEndToEndTest {

  private WireMockServer server;
  private HttpSecretsClient secretsClient;
  private JsonMapper mapper;

  @BeforeEach
  void start() {
    server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    server.start();
    secretsClient = new HttpSecretsClient(URI.create(server.baseUrl()));
    mapper = new SecretResolvingJsonMapper(new CamundaObjectMapper(), secretsClient);
  }

  @AfterEach
  void stop() {
    server.stop();
  }

  @Test
  void shouldResolveReferenceEndToEnd() {
    server.stubFor(
        post(urlEqualTo("/v2/secrets/resolve"))
            .withRequestBody(equalToJson("{\"references\":[\"camunda.secrets.MY_TOKEN\"]}"))
            .willReturn(okJson("{\"resolved\":{\"camunda.secrets.MY_TOKEN\":\"sk-abc123\"}}")));

    final var variables =
        mapper.fromJsonAsMap("{\"token\":\"Bearer camunda.secrets.MY_TOKEN\",\"plain\":\"hello\"}");

    assertThat(variables)
        .containsEntry("token", "Bearer sk-abc123")
        .containsEntry("plain", "hello");
    server.verify(postRequestedFor(urlEqualTo("/v2/secrets/resolve")));
  }

  @Test
  void shouldLeaveReferenceUnchangedWhenGatewayOmitsIt() {
    server.stubFor(post(urlEqualTo("/v2/secrets/resolve")).willReturn(okJson("{\"resolved\":{}}")));

    final var variables = mapper.fromJsonAsMap("{\"token\":\"camunda.secrets.UNKNOWN\"}");

    assertThat(variables).containsEntry("token", "camunda.secrets.UNKNOWN");
  }
}
