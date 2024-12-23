/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.spring.client.config;

import static org.assertj.core.api.Assertions.*;

import io.camunda.client.CredentialsProvider;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.impl.CamundaObjectMapper;
import io.camunda.spring.client.configuration.CamundaClientConfigurationImpl;
import io.camunda.spring.client.jobhandling.CamundaClientExecutorService;
import io.camunda.spring.client.properties.CamundaClientConfigurationProperties;
import io.camunda.spring.client.properties.CamundaClientProperties;
import io.grpc.ClientInterceptor;
import java.util.List;
import org.apache.hc.client5.http.async.AsyncExecChainHandler;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

public class CamundaClientConfigurationImplTest {
  private static CamundaClientConfigurationImpl configuration(
      final CamundaClientConfigurationProperties legacyProperties,
      final CamundaClientProperties properties,
      final JsonMapper jsonMapper,
      final List<ClientInterceptor> interceptors,
      final List<AsyncExecChainHandler> chainHandlers,
      final CamundaClientExecutorService executorService) {
    return new CamundaClientConfigurationImpl(
        legacyProperties, properties, jsonMapper, interceptors, chainHandlers, executorService);
  }

  private static CamundaClientConfigurationProperties legacyProperties() {
    return new CamundaClientConfigurationProperties(new MockEnvironment());
  }

  private static CamundaClientProperties properties() {
    return new CamundaClientProperties();
  }

  private static JsonMapper jsonMapper() {
    return new CamundaObjectMapper();
  }

  private static CamundaClientExecutorService executorService() {
    return CamundaClientExecutorService.createDefault();
  }

  @Test
  void shouldCreateSingletonCredentialProvider() {
    final CamundaClientConfigurationImpl configuration =
        configuration(
            legacyProperties(),
            properties(),
            jsonMapper(),
            List.of(),
            List.of(),
            executorService());
    final CredentialsProvider credentialsProvider1 = configuration.getCredentialsProvider();
    final CredentialsProvider credentialsProvider2 = configuration.getCredentialsProvider();
    assertThat(credentialsProvider1).isSameAs(credentialsProvider2);
  }
}
