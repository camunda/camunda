/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.spring.client.config;

import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static org.assertj.core.api.Assertions.assertThat;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.camunda.client.CredentialsProvider;
import io.camunda.client.impl.oauth.OAuthCredentialsProvider;
import io.camunda.spring.client.configuration.CamundaClientConfigurationImpl;
import io.camunda.spring.client.configuration.JsonMapperConfiguration;
import io.camunda.spring.client.jobhandling.CamundaClientExecutorService;
import io.camunda.spring.client.properties.CamundaClientConfigurationProperties;
import io.camunda.spring.client.properties.CamundaClientProperties;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
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
    classes = {JsonMapperConfiguration.class, CamundaClientConfigurationImpl.class},
    properties = {
      "camunda.client.mode=self-managed",
      "camunda.client.auth.client-id=my-client-id",
      "camunda.client.auth.client-secret=my-client-secret",
      "camunda.client.auth.keystore-password=password",
      "camunda.client.auth.keystore-key-password=password",
      "camunda.client.auth.truststore-password=password",
    })
@EnableConfigurationProperties({
  CamundaClientConfigurationProperties.class,
  CamundaClientProperties.class
})
public class CredentialsProviderSelfManagedWithSSLTest {

  private static final String VALID_TRUSTSTORE_PATH =
      CredentialsProviderSelfManagedWithSSLTest.class
          .getClassLoader()
          .getResource("idp-ssl/truststore.jks")
          .getPath();

  private static final String VALID_IDENTITY_PATH =
      CredentialsProviderSelfManagedWithSSLTest.class
          .getClassLoader()
          .getResource("idp-ssl/identity.p12")
          .getPath();

  @RegisterExtension
  static WireMockExtension wm =
      WireMockExtension.newInstance()
          .options(
              new WireMockConfiguration()
                  .keystorePath(VALID_IDENTITY_PATH)
                  .keystorePassword("password")
                  .trustStorePath(VALID_TRUSTSTORE_PATH)
                  .trustStorePassword("password")
                  .dynamicHttpsPort())
          .build();

  private static final String VALID_CLIENT_PATH =
      CredentialsProviderSelfManagedWithSSLTest.class
          .getClassLoader()
          .getResource("idp-ssl/localhost.p12")
          .getPath();
  private static final String ACCESS_TOKEN =
      JWT.create().withExpiresAt(Instant.now().plusSeconds(300)).sign(Algorithm.none());

  @MockBean
  CamundaClientExecutorService zeebeClientExecutorService;
  @Autowired CamundaClientConfigurationImpl configuration;

  @DynamicPropertySource
  static void registerPgProperties(final DynamicPropertyRegistry registry) {
    final String issuer = "https://localhost:" + wm.getHttpsPort() + "/auth-server";
    registry.add("camunda.client.auth.issuer", () -> issuer);
    registry.add("camunda.client.auth.keystore-path", () -> VALID_CLIENT_PATH);
    registry.add("camunda.client.auth.truststore-path", () -> VALID_TRUSTSTORE_PATH);
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
    final CredentialsProvider credentialsProvider = configuration.getCredentialsProvider();
    assertThat(credentialsProvider).isExactlyInstanceOf(OAuthCredentialsProvider.class);
  }

  @Test
  @Disabled
  void shouldHaveZeebeAuth() throws IOException {
    final CredentialsProvider credentialsProvider = configuration.getCredentialsProvider();
    final Map<String, String> headers = new HashMap<>();

    wm.stubFor(
        post("/auth-server")
            .willReturn(
                ok().withJsonBody(
                        JsonNodeFactory.instance
                            .objectNode()
                            .put("access_token", ACCESS_TOKEN)
                            .put("token_type", "bearer")
                            .put("expires_in", 300))));

    credentialsProvider.applyCredentials(headers::put);
    assertThat(credentialsProvider).isExactlyInstanceOf(OAuthCredentialsProvider.class);
    assertThat(headers).containsEntry("Authorization", "Bearer " + ACCESS_TOKEN);
    assertThat(headers).hasSize(1);
  }
}
