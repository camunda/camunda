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
package io.camunda.spring.client.config;

import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static org.assertj.core.api.Assertions.assertThat;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
<<<<<<< HEAD:clients/spring-boot-starter-camunda-sdk/src/test/java/io/camunda/spring/client/config/CredentialsProviderSelfManagedTest.java
import io.camunda.client.CredentialsProvider;
import io.camunda.client.impl.oauth.OAuthCredentialsProvider;
import io.camunda.spring.client.configuration.CredentialsProviderConfiguration;
import io.camunda.spring.client.jobhandling.CamundaClientExecutorService;
import io.camunda.spring.client.properties.CamundaClientProperties;
=======
import io.camunda.zeebe.client.CredentialsProvider;
import io.camunda.zeebe.client.impl.oauth.OAuthCredentialsProvider;
import io.camunda.zeebe.spring.client.configuration.CredentialsProviderConfiguration;
import io.camunda.zeebe.spring.client.configuration.JsonMapperConfiguration;
import io.camunda.zeebe.spring.client.configuration.ZeebeClientConfigurationImpl;
import io.camunda.zeebe.spring.client.jobhandling.ZeebeClientExecutorService;
import io.camunda.zeebe.spring.client.properties.CamundaClientProperties;
>>>>>>> 94c106bd (feat: new property mapping mechanism, just like in 8.8):clients/spring-boot-starter-camunda-sdk/src/test/java/io/camunda/zeebe/spring/client/config/CredentialsProviderSelfManagedTest.java
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import wiremock.com.fasterxml.jackson.databind.node.JsonNodeFactory;

@SpringBootTest(
<<<<<<< HEAD:clients/spring-boot-starter-camunda-sdk/src/test/java/io/camunda/spring/client/config/CredentialsProviderSelfManagedTest.java
    classes = {CredentialsProviderConfiguration.class},
=======
    classes = {
      JsonMapperConfiguration.class,
      ZeebeClientConfigurationImpl.class,
      CredentialsProviderConfiguration.class
    },
>>>>>>> 94c106bd (feat: new property mapping mechanism, just like in 8.8):clients/spring-boot-starter-camunda-sdk/src/test/java/io/camunda/zeebe/spring/client/config/CredentialsProviderSelfManagedTest.java
    properties = {
      "camunda.client.mode=self-managed",
      "camunda.client.auth.client-id=my-client-id",
      "camunda.client.auth.client-secret=my-client-secret"
    })
@EnableConfigurationProperties({CamundaClientProperties.class})
public class CredentialsProviderSelfManagedTest {

  @RegisterExtension
  static WireMockExtension wm =
      WireMockExtension.newInstance().options(new WireMockConfiguration().dynamicPort()).build();

<<<<<<< HEAD:clients/spring-boot-starter-camunda-sdk/src/test/java/io/camunda/spring/client/config/CredentialsProviderSelfManagedTest.java
  @MockBean CamundaClientExecutorService zeebeClientExecutorService;
  @Autowired CredentialsProvider credentialsProvider;
=======
  @MockitoBean ZeebeClientExecutorService zeebeClientExecutorService;
  @Autowired ZeebeClientConfigurationImpl configuration;
>>>>>>> 94c106bd (feat: new property mapping mechanism, just like in 8.8):clients/spring-boot-starter-camunda-sdk/src/test/java/io/camunda/zeebe/spring/client/config/CredentialsProviderSelfManagedTest.java

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
  void shouldBeSelfManaged() {
    assertThat(credentialsProvider).isExactlyInstanceOf(OAuthCredentialsProvider.class);
  }

  @Test
  void shouldHaveZeebeAuth() throws IOException {
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
    credentialsProvider.applyCredentials(headers::put);
    assertThat(credentialsProvider).isExactlyInstanceOf(OAuthCredentialsProvider.class);
    assertThat(headers).containsEntry("Authorization", "Bearer " + accessToken);
    assertThat(headers).hasSize(1);
  }
}
