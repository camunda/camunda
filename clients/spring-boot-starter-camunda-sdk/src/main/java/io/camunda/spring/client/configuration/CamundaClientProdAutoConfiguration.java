/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.spring.client.configuration;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.impl.CamundaClientImpl;
import io.camunda.client.impl.util.ExecutorResource;
import io.camunda.spring.client.jobhandling.CamundaClientExecutorService;
import io.camunda.spring.client.properties.CamundaClientConfigurationProperties;
import io.camunda.spring.client.properties.CamundaClientProperties;
import io.camunda.spring.client.testsupport.CamundaSpringProcessTestContext;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc;
import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannel;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import org.apache.hc.client5.http.async.AsyncExecChainHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/*
 * All configurations that will only be used in production code - meaning NO TEST cases
 */
@ConditionalOnProperty(
    prefix = "camunda.client",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@ConditionalOnMissingBean(CamundaSpringProcessTestContext.class)
@ImportAutoConfiguration({
  ExecutorServiceConfiguration.class,
  CamundaActuatorConfiguration.class,
  JsonMapperConfiguration.class,
})
@AutoConfigureBefore(CamundaClientAllAutoConfiguration.class)
public class CamundaClientProdAutoConfiguration {

  private static final Logger LOG =
      LoggerFactory.getLogger(CamundaClientProdAutoConfiguration.class);

  @Bean
  public CamundaClientConfigurationImpl zeebeClientConfiguration(
      final CamundaClientConfigurationProperties properties,
      final CamundaClientProperties camundaClientProperties,
      final JsonMapper jsonMapper,
      final List<ClientInterceptor> interceptors,
      final List<AsyncExecChainHandler> chainHandlers,
      final CamundaClientExecutorService zeebeClientExecutorService) {
    return new CamundaClientConfigurationImpl(
        properties,
        camundaClientProperties,
        jsonMapper,
        interceptors,
        chainHandlers,
        zeebeClientExecutorService) {};
  }

  @Bean(destroyMethod = "close")
  public CamundaClient camundaClient(final CamundaClientConfigurationImpl configuration) {
    LOG.info("Creating ZeebeClient using ZeebeClientConfigurationImpl [" + configuration + "]");
    final ScheduledExecutorService jobWorkerExecutor = configuration.jobWorkerExecutor();
    if (jobWorkerExecutor != null) {
      final ManagedChannel managedChannel = CamundaClientImpl.buildChannel(configuration);
      final GatewayGrpc.GatewayStub gatewayStub =
          CamundaClientImpl.buildGatewayStub(managedChannel, configuration);
      final ExecutorResource executorResource =
          new ExecutorResource(jobWorkerExecutor, configuration.ownsJobWorkerExecutor());
      return new CamundaClientImpl(configuration, managedChannel, gatewayStub, executorResource);
    } else {
      return new CamundaClientImpl(configuration);
    }
  }
}
