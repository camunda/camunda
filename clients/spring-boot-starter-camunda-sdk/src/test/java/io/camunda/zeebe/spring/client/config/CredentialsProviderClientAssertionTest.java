/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.spring.client.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.client.CredentialsProvider;
import io.camunda.zeebe.client.impl.oauth.OAuthCredentialsProvider;
import io.camunda.zeebe.spring.client.configuration.CredentialsProviderConfiguration;
import io.camunda.zeebe.spring.client.configuration.JsonMapperConfiguration;
import io.camunda.zeebe.spring.client.configuration.ZeebeClientConfigurationImpl;
import io.camunda.zeebe.spring.client.jobhandling.ZeebeClientExecutorService;
import io.camunda.zeebe.spring.client.properties.CamundaClientProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
    classes = {
      JsonMapperConfiguration.class,
      ZeebeClientConfigurationImpl.class,
      CredentialsProviderConfiguration.class
    },
    properties = {
      "camunda.client.mode=self-managed",
      "camunda.client.auth.client-id=CredentialsProviderSelfManagedTest-my-client-id",
      "camunda.client.auth.client-secret=my-client-secret",
      "camunda.client.auth.client-assertion.keystore-password=mstest"
    })
@EnableConfigurationProperties(CamundaClientProperties.class)
public class CredentialsProviderClientAssertionTest {
  @Autowired CredentialsProvider credentialsProvider;
  @MockitoBean ZeebeClientExecutorService zeebeClientExecutorService;

  @DynamicPropertySource
  static void properties(final DynamicPropertyRegistry registry) {
    registry.add(
        "camunda.client.auth.client-assertion.keystore-path",
        () ->
            CredentialsProviderClientAssertionTest.class
                .getClassLoader()
                .getResource("keystore/test.jks")
                .getPath());
  }

  @Test
  void shouldBuildCredentialsProviderWithClientAssertion() {
    assertThat(credentialsProvider).isInstanceOf(OAuthCredentialsProvider.class);
    final OAuthCredentialsProvider oAuthCredentialsProvider =
        (OAuthCredentialsProvider) credentialsProvider;
    assertThat(oAuthCredentialsProvider.isClientAssertionEnabled()).isTrue();
  }
}
