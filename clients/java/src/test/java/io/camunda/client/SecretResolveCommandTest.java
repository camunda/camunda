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

import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.camunda.client.api.response.SecretResolveResponse;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@WireMockTest
public class SecretResolveCommandTest {

  private CamundaClient client;

  @BeforeEach
  void setUp(final WireMockRuntimeInfo mockInfo) throws URISyntaxException {
    client =
        CamundaClient.newClientBuilder()
            .preferRestOverGrpc(true)
            .restAddress(new URI(mockInfo.getHttpBaseUrl()))
            .build();
  }

  @AfterEach
  void tearDown() {
    if (client != null) {
      client.close();
    }
  }

  @Test
  void shouldPostReferencesAndReturnResolved(final WireMockRuntimeInfo mockInfo) {
    // given
    mockInfo
        .getWireMock()
        .register(
            post(urlEqualTo("/v2/secrets/resolve"))
                .withRequestBody(
                    equalToJson(
                        "{\"references\":[\"camunda.secrets.FOO\",\"camunda.secrets.BAR\"]}"))
                .willReturn(
                    okJson(
                        "{\"resolved\":{\"camunda.secrets.FOO\":\"foo-value\","
                            + "\"camunda.secrets.BAR\":\"bar-value\"}}")));

    // when
    final SecretResolveResponse response =
        client
            .newSecretResolveCommand()
            .references("camunda.secrets.FOO", "camunda.secrets.BAR")
            .send()
            .join();

    // then
    assertThat(response.getResolved())
        .containsEntry("camunda.secrets.FOO", "foo-value")
        .containsEntry("camunda.secrets.BAR", "bar-value");
    mockInfo.getWireMock().verifyThat(postRequestedFor(urlEqualTo("/v2/secrets/resolve")));
  }

  @Test
  void shouldAccumulateReferencesAcrossCalls(final WireMockRuntimeInfo mockInfo) {
    // given
    mockInfo
        .getWireMock()
        .register(
            post(urlEqualTo("/v2/secrets/resolve"))
                .withRequestBody(
                    equalToJson(
                        "{\"references\":[\"camunda.secrets.A\","
                            + "\"camunda.secrets.B\",\"camunda.secrets.C\"]}"))
                .willReturn(okJson("{\"resolved\":{}}")));

    // when
    client
        .newSecretResolveCommand()
        .references("camunda.secrets.A")
        .references(Arrays.asList("camunda.secrets.B", "camunda.secrets.C"))
        .send()
        .join();

    // then
    mockInfo.getWireMock().verifyThat(postRequestedFor(urlEqualTo("/v2/secrets/resolve")));
  }

  @Test
  void shouldHandleEmptyResolvedMap(final WireMockRuntimeInfo mockInfo) {
    // given
    mockInfo
        .getWireMock()
        .register(post(urlEqualTo("/v2/secrets/resolve")).willReturn(okJson("{\"resolved\":{}}")));

    // when
    final SecretResolveResponse response =
        client.newSecretResolveCommand().references("camunda.secrets.UNKNOWN").send().join();

    // then
    assertThat(response.getResolved()).isEmpty();
  }

  @Test
  void shouldHandleMissingResolvedField(final WireMockRuntimeInfo mockInfo) {
    // given — response without "resolved" field
    mockInfo
        .getWireMock()
        .register(post(urlEqualTo("/v2/secrets/resolve")).willReturn(okJson("{}")));

    // when
    final SecretResolveResponse response =
        client.newSecretResolveCommand().references("camunda.secrets.FOO").send().join();

    // then
    assertThat(response.getResolved()).isEmpty();
  }

  @Test
  void shouldResolveTransparentlyDuringVariableDeserialization(final WireMockRuntimeInfo mockInfo)
      throws URISyntaxException {
    // given — a client built with .withSecretResolution()
    final CamundaClient transparentClient =
        CamundaClient.newClientBuilder()
            .preferRestOverGrpc(true)
            .restAddress(new URI(mockInfo.getHttpBaseUrl()))
            .withSecretResolution()
            .build();
    try {
      WireMock.configureFor(mockInfo.getHttpPort());
      mockInfo
          .getWireMock()
          .register(
              post(urlEqualTo("/v2/secrets/resolve"))
                  .willReturn(
                      okJson("{\"resolved\":{\"camunda.secrets.TOKEN\":\"sk_live_xyz\"}}")));

      // when — simulate the same path the worker takes
      final Map<String, Object> variables =
          transparentClient
              .getConfiguration()
              .getJsonMapper()
              .fromJsonAsMap("{\"auth\":\"Bearer camunda.secrets.TOKEN\"}");

      // then
      assertThat(variables).containsEntry("auth", "Bearer sk_live_xyz");
    } finally {
      transparentClient.close();
    }
  }
}
