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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class RemoteClientConfiguration {

  @Bean
  @Primary
  @ConfigurationProperties(prefix = "io.camunda.process.test.remote.client")
  CamundaClientProperties remoteClientProperties() {
    return new CamundaClientProperties();
  }

  @Bean
  public CamundaClientBuilderFactory remoteClientBuilderFactory(
      @Qualifier("remoteClientProperties") final CamundaClientProperties remoteClientProperties) {

    final CamundaClientBuilder remoteClientBuilder =
        createRemoteClientBuilder(remoteClientProperties);

    if (remoteClientProperties.getRestAddress() != null) {
      remoteClientBuilder.restAddress(remoteClientProperties.getRestAddress());
    }
    if (remoteClientProperties.getGrpcAddress() != null) {
      remoteClientBuilder.grpcAddress(remoteClientProperties.getGrpcAddress());
    }

    return () -> remoteClientBuilder;
  }

  private CamundaClientBuilder createRemoteClientBuilder(
      final CamundaClientProperties camundaClientProperties) {
    if (camundaClientProperties.getMode() == ClientMode.saas) {
      final CamundaClientCloudProperties cloudProperties = camundaClientProperties.getCloud();
      final CamundaClientAuthProperties authProperties = camundaClientProperties.getAuth();

      final CredentialsProvider credentialsProvider =
          new CredentialsProviderConfiguration()
              .camundaClientCredentialsProvider(camundaClientProperties);

      return CamundaClient.newCloudClientBuilder()
          .withClusterId(cloudProperties.getClusterId())
          .withClientId(authProperties.getClientId())
          .withClientSecret(authProperties.getClientSecret())
          .withRegion(cloudProperties.getRegion())
          .credentialsProvider(credentialsProvider);

    } else {
      return CamundaClient.newClientBuilder().usePlaintext();
    }
  }
}
