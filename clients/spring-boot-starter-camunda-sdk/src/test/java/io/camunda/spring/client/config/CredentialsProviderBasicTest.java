/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.spring.client.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CredentialsProvider;
import io.camunda.client.impl.basicauth.BasicAuthCredentialsProvider;
import io.camunda.spring.client.configuration.CredentialsProviderConfiguration;
import io.camunda.spring.client.properties.CamundaClientProperties;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = {CredentialsProviderConfiguration.class},
    properties = {
      "camunda.client.mode=basic",
      "camunda.client.auth.username=foo",
      "camunda.client.auth.password=bar"
    })
@EnableConfigurationProperties({CamundaClientProperties.class})
public class CredentialsProviderBasicTest {
  public static final String USERNAME = "foo";
  public static final String PASSWORD = "bar";
  @Autowired CredentialsProvider credentialsProvider;

  @Test
  void shouldCreateBasicAuthCredentialsProvider() {
    assertThat(credentialsProvider).isExactlyInstanceOf(BasicAuthCredentialsProvider.class);
  }

  @Test
  void shouldHaveBasicAuthHeader() throws IOException {
    final Map<String, String> headers = new HashMap<>();
    final var encodedCredentials =
        Base64.getEncoder().encodeToString(String.format("%s:%s", USERNAME, PASSWORD).getBytes());

    credentialsProvider.applyCredentials(headers::put);
    assertThat(headers).isEqualTo(Map.of("Authorization", "Basic " + encodedCredentials));
  }
}
