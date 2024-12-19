/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.client.util;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientBuilder;
import io.camunda.client.impl.CamundaClientBuilderImpl;
import io.camunda.client.impl.CamundaClientImpl;
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
  private final Consumer<CamundaClientBuilder> clientConfigurator;

  private RecordingGatewayService gatewayService;
  private CamundaClientImpl client;
  private GatewayStub gatewayStub;
  private final CamundaClientBuilderImpl builder = new CamundaClientBuilderImpl();

  public TestEnvironmentRule() {
    this(b -> {});
  }

  public TestEnvironmentRule(final Consumer<CamundaClientBuilder> clientConfigurator) {
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
    gatewayStub = spy(CamundaClientImpl.buildGatewayStub(channel, builder));
    client = new CamundaClientImpl(builder, channel, gatewayStub);
  }

  @Override
  protected void after() {
    if (client != null) {
      client.close();
      client = null;
    }
  }

  public CamundaClient getClient() {
    return client;
  }

  public CamundaClientBuilderImpl getClientBuilder() {
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
