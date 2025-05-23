/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.impl.configuration;

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientBuilder;
import io.camunda.client.CredentialsProvider;
import io.camunda.process.test.api.CamundaClientBuilderFactory;
import io.camunda.spring.client.configuration.CredentialsProviderConfiguration;
import io.camunda.spring.client.properties.CamundaClientAuthProperties;
import io.camunda.spring.client.properties.CamundaClientCloudProperties;
import io.camunda.spring.client.properties.CamundaClientProperties;
import io.camunda.spring.client.properties.CamundaClientProperties.ClientMode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "io.camunda.process.test.remote")
public class RemoteClientConfiguration {

  @NestedConfigurationProperty
  private CamundaClientProperties client = new CamundaClientProperties();

  public CamundaClientProperties getClient() {
    return client;
  }

  public void setClient(final CamundaClientProperties client) {
    this.client = client;
  }

  @Bean
  public CamundaClientBuilderFactory remoteClientBuilderFactory() {
    final CamundaClientProperties remoteClientProperties = client;

    final CamundaClientBuilder remoteClientBuilder =
        createCamundaClientBuilder(remoteClientProperties);

    if (remoteClientProperties.getRestAddress() != null) {
      remoteClientBuilder.restAddress(remoteClientProperties.getRestAddress());
    }
    if (remoteClientProperties.getGrpcAddress() != null) {
      remoteClientBuilder.grpcAddress(remoteClientProperties.getGrpcAddress());
    }

    return () -> remoteClientBuilder;
  }

  private static CamundaClientBuilder createCamundaClientBuilder(
      final CamundaClientProperties clientProperties) {

    if (clientProperties.getMode() == ClientMode.saas) {
      return createCloudClientBuilder(clientProperties);

    } else {
      return CamundaClient.newClientBuilder().usePlaintext();
    }
  }

  private static CamundaClientBuilder createCloudClientBuilder(
      final CamundaClientProperties clientProperties) {
    final CamundaClientCloudProperties cloudProperties = clientProperties.getCloud();
    final CamundaClientAuthProperties authProperties = clientProperties.getAuth();

    final CredentialsProvider credentialsProvider = createCredentialsProvider(clientProperties);

    return CamundaClient.newCloudClientBuilder()
        .withClusterId(cloudProperties.getClusterId())
        .withClientId(authProperties.getClientId())
        .withClientSecret(authProperties.getClientSecret())
        .withRegion(cloudProperties.getRegion())
        .credentialsProvider(credentialsProvider);
  }

  private static CredentialsProvider createCredentialsProvider(
      final CamundaClientProperties clientProperties) {
    return new CredentialsProviderConfiguration()
        .camundaClientCredentialsProvider(clientProperties);
  }
}
