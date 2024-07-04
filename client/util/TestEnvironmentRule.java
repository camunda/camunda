/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.client.util;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.ZeebeClientBuilder;
import io.camunda.zeebe.client.impl.ZeebeClientBuilderImpl;
import io.camunda.zeebe.client.impl.ZeebeClientImpl;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.grpc.ManagedChannel;
import io.grpc.testing.GrpcServerRule;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.rules.ExternalResource;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public final class TestEnvironmentRule extends ExternalResource {

  private final GrpcServerRule serverRule = new GrpcServerRule();
  private final Consumer<ZeebeClientBuilder> clientConfigurator;

  private RecordingGatewayService gatewayService;
  private ZeebeClientImpl client;
  private GatewayStub gatewayStub;
  private final ZeebeClientBuilderImpl builder = new ZeebeClientBuilderImpl();

  public TestEnvironmentRule() {
    this(b -> {});
  }

  public TestEnvironmentRule(final Consumer<ZeebeClientBuilder> clientConfigurator) {
    this.clientConfigurator = clientConfigurator;
  }

  @Override
  public Statement apply(final Statement base, final Description description) {
    final Statement statement = super.apply(base, description);
    return serverRule.apply(statement, description);
  }

  @Override
  protected void before() {
    gatewayService = new RecordingGatewayService();
    serverRule.getServiceRegistry().addService(gatewayService);

    final ManagedChannel channel = serverRule.getChannel();
    clientConfigurator.accept(builder);
    gatewayStub = spy(ZeebeClientImpl.buildGatewayStub(channel, builder));
    client = new ZeebeClientImpl(builder, channel, gatewayStub);
  }

  @Override
  protected void after() {
    if (client != null) {
      client.close();
      client = null;
    }
  }

  public ZeebeClient getClient() {
    return client;
  }

  public ZeebeClientBuilderImpl getClientBuilder() {
    return builder;
  }

  public RecordingGatewayService getGatewayService() {
    return gatewayService;
  }

  public GatewayStub getGatewayStub() {
    return gatewayStub;
  }

  public void verifyDefaultRequestTimeout() {
    verifyRequestTimeout(client.getConfiguration().getDefaultRequestTimeout());
  }

  public void verifyRequestTimeout(final Duration requestTimeout) {
    verify(gatewayStub).withDeadlineAfter(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
  }
}
