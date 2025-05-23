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
import io.camunda.client.CamundaClientConfiguration;
import io.camunda.client.CredentialsProvider;
import io.camunda.client.api.JsonMapper;
import io.camunda.process.test.api.CamundaClientBuilderFactory;
import io.camunda.spring.client.configuration.CamundaClientConfigurationImpl;
import io.camunda.spring.client.jobhandling.CamundaClientExecutorService;
import io.grpc.ClientInterceptor;
import java.util.List;
import org.apache.hc.client5.http.async.AsyncExecChainHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RemoteClientConfiguration {

  @Bean
  public CamundaClientBuilderFactory remoteClientBuilderFactory(
      final CamundaClientConfiguration remoteClientConfiguration) {
    final CamundaClientBuilder remoteClientBuilder =
        CamundaClient.newClientBuilder()
            .restAddress(remoteClientConfiguration.getRestAddress())
            .grpcAddress(remoteClientConfiguration.getGrpcAddress());

    if (remoteClientConfiguration.isPlaintextConnectionEnabled()) {
      remoteClientBuilder.usePlaintext();
    }

    remoteClientBuilder.credentialsProvider(remoteClientConfiguration.getCredentialsProvider());

    return () -> remoteClientBuilder;
  }

  @Bean
  public CamundaClientConfiguration remoteCamundaClientConfiguration(
      final CamundaContainerRuntimeConfiguration camundaContainerRuntimeConfiguration,
      final JsonMapper jsonMapper,
      final List<ClientInterceptor> interceptors,
      final List<AsyncExecChainHandler> chainHandlers,
      final CamundaClientExecutorService camundaClientExecutorService,
      final CredentialsProvider camundaClientCredentialsProvider) {
    return new CamundaClientConfigurationImpl(
        camundaContainerRuntimeConfiguration.getRemote().getClient(),
        jsonMapper,
        interceptors,
        chainHandlers,
        camundaClientExecutorService,
        camundaClientCredentialsProvider);
  }
}
