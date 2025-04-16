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
package io.camunda.zeebe.spring.client.config;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.camunda.zeebe.client.CredentialsProvider;
import io.camunda.zeebe.client.impl.oauth.OAuthCredentialsProvider;
import io.camunda.zeebe.spring.client.configuration.CredentialsProviderConfiguration;
import io.camunda.zeebe.spring.client.configuration.JsonMapperConfiguration;
import io.camunda.zeebe.spring.client.configuration.ZeebeClientConfigurationImpl;
import io.camunda.zeebe.spring.client.jobhandling.ZeebeClientExecutorService;
import io.camunda.zeebe.spring.client.properties.CamundaClientProperties;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import wiremock.com.fasterxml.jackson.databind.node.JsonNodeFactory;

@SpringBootTest(
    classes = {
      JsonMapperConfiguration.class,
      ZeebeClientConfigurationImpl.class,
      CredentialsProviderConfiguration.class
    },
    properties = {
      "camunda.client.mode=saas",
      "camunda.client.cluster-id=12345",
      "camunda.client.region=bru-2",
      "camunda.client.auth.client-id=CredentialsProviderSaasTest-my-client-id",
      "camunda.client.auth.client-secret=my-client-secret"
    })
@EnableConfigurationProperties({CamundaClientProperties.class})
public class CredentialsProviderSaasTest {

  @RegisterExtension
  static WireMockExtension wm =
      WireMockExtension.newInstance().options(new WireMockConfiguration().dynamicPort()).build();

  @MockBean ZeebeClientExecutorService zeebeClientExecutorService;
  @Autowired ZeebeClientConfigurationImpl configuration;

  @DynamicPropertySource
  static void registerPgProperties(final DynamicPropertyRegistry registry) {
    final String issuer = "http://localhost:" + wm.getPort() + "/auth-server";
    registry.add("camunda.client.auth.token-url", () -> issuer);
  }

  @BeforeEach
  void setUp() {
    // Clean up credentials cache to ensure every test gets fresh token
    Paths.get(System.getProperty("user.home"), ".camunda", "credentials")
        .toAbsolutePath()
        .toFile()
        .delete();
  }

  @Test
  void shouldBeSaas() {
    final CredentialsProvider credentialsProvider = configuration.getCredentialsProvider();
    assertThat(credentialsProvider).isExactlyInstanceOf(OAuthCredentialsProvider.class);
  }

  @Test
  void shouldHaveZeebeAuth() throws IOException {
    final CredentialsProvider credentialsProvider = configuration.getCredentialsProvider();
    final Map<String, String> headers = new HashMap<>();

    final String accessToken =
        JWT.create().withExpiresAt(Instant.now().plusSeconds(300)).sign(Algorithm.none());
    wm.stubFor(
        post("/auth-server")
            .willReturn(
                ok().withJsonBody(
                        JsonNodeFactory.instance
                            .objectNode()
                            .put("access_token", accessToken)
                            .put("token_type", "bearer")
                            .put("expires_in", 300))));

    credentialsProvider.applyCredentials(headers::put);
    assertThat(headers).isEqualTo(Map.of("Authorization", "Bearer " + accessToken));
    wm.verify(
        postRequestedFor(urlEqualTo("/auth-server"))
            .withHeader("Content-Type", equalTo("application/x-www-form-urlencoded")));
  }
}
