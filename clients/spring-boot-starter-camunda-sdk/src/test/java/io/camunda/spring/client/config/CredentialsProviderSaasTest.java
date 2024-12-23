/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.spring.client.config;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

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
    classes = {JsonMapperConfiguration.class, CamundaClientConfigurationImpl.class},
    properties = {
      "camunda.client.mode=saas",
      "camunda.client.cluster-id=12345",
      "camunda.client.region=bru-2",
      "camunda.client.auth.client-id=my-client-id",
      "camunda.client.auth.client-secret=my-client-secret"
    })
@EnableConfigurationProperties({
  CamundaClientConfigurationProperties.class,
  CamundaClientProperties.class
})
public class CredentialsProviderSaasTest {

  @RegisterExtension
  static WireMockExtension wm =
      WireMockExtension.newInstance().options(new WireMockConfiguration().dynamicPort()).build();

  private static final String ACCESS_TOKEN = "access-token";
  @MockBean
  CamundaClientExecutorService zeebeClientExecutorService;
  @Autowired CamundaClientConfigurationImpl configuration;

  @DynamicPropertySource
  static void registerPgProperties(final DynamicPropertyRegistry registry) {
    final String issuer = "http://localhost:" + wm.getPort() + "/auth-server";
    registry.add("camunda.client.auth.issuer", () -> issuer);
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

    final String accessToken = ACCESS_TOKEN;
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
