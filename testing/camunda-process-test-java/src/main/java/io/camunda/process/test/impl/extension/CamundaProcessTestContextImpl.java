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
package io.camunda.process.test.impl.extension;

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientBuilder;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.impl.client.CamundaManagementClient;
import io.camunda.process.test.impl.containers.CamundaContainer;
import io.camunda.process.test.impl.containers.ConnectorsContainer;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.ZeebeClientBuilder;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;

public class CamundaProcessTestContextImpl implements CamundaProcessTestContext {

  private final CamundaContainer camundaContainer;
  private final ConnectorsContainer connectorsContainer;
  private final Consumer<AutoCloseable> clientCreationCallback;
  private final CamundaManagementClient camundaManagementClient;

  public CamundaProcessTestContextImpl(
      final CamundaContainer camundaContainer,
      final ConnectorsContainer connectorsContainer,
      final Consumer<AutoCloseable> clientCreationCallback,
      final CamundaManagementClient camundaManagementClient) {
    this.camundaContainer = camundaContainer;
    this.connectorsContainer = connectorsContainer;
    this.clientCreationCallback = clientCreationCallback;
    this.camundaManagementClient = camundaManagementClient;
  }

  @Override
  public CamundaClient createClient() {
    return createClient(builder -> {});
  }

  @Override
  public CamundaClient createClient(final Consumer<CamundaClientBuilder> modifier) {
    final CamundaClientBuilder builder =
        CamundaClient.newClientBuilder()
            .usePlaintext()
            .grpcAddress(getCamundaGrpcAddress())
            .restAddress(getCamundaRestAddress());

    modifier.accept(builder);

    final CamundaClient client = builder.build();
    clientCreationCallback.accept(client);

    return client;
  }

  @Override
  public ZeebeClient createZeebeClient() {
    return createZeebeClient(builder -> {});
  }

  @Override
  public ZeebeClient createZeebeClient(final Consumer<ZeebeClientBuilder> modifier) {
    final ZeebeClientBuilder builder =
        ZeebeClient.newClientBuilder()
            .usePlaintext()
            .grpcAddress(getCamundaGrpcAddress())
            .restAddress(getCamundaRestAddress());

    modifier.accept(builder);

    final ZeebeClient client = builder.build();
    clientCreationCallback.accept(client);

    return client;
  }

  @Override
  public URI getCamundaGrpcAddress() {
    return camundaContainer.getGrpcApiAddress();
  }

  @Override
  public URI getCamundaRestAddress() {
    return camundaContainer.getRestApiAddress();
  }

  @Override
  public URI getConnectorsAddress() {
    return connectorsContainer.getRestApiAddress();
  }

  @Override
  public Instant getCurrentTime() {
    return camundaManagementClient.getCurrentTime();
  }

  @Override
  public void increaseTime(final Duration timeToAdd) {
    camundaManagementClient.increaseTime(timeToAdd);
  }
}
