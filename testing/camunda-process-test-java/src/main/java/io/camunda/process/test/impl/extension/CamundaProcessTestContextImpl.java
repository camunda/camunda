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

import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.impl.containers.ZeebeContainer;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.ZeebeClientBuilder;
import java.net.URI;
import java.util.function.Consumer;

public class CamundaProcessTestContextImpl implements CamundaProcessTestContext {

  private final ZeebeContainer zeebeContainer;
  private final Consumer<ZeebeClient> clientCreationCallback;

  public CamundaProcessTestContextImpl(
      final ZeebeContainer zeebeContainer, final Consumer<ZeebeClient> clientCreationCallback) {
    this.zeebeContainer = zeebeContainer;
    this.clientCreationCallback = clientCreationCallback;
  }

  @Override
  public ZeebeClient createClient() {
    return createClient(builder -> {});
  }

  @Override
  public ZeebeClient createClient(final Consumer<ZeebeClientBuilder> modifier) {
    final ZeebeClientBuilder builder =
        ZeebeClient.newClientBuilder()
            .usePlaintext()
            .grpcAddress(getZeebeGrpcAddress())
            .restAddress(getZeebeRestAddress());

    modifier.accept(builder);

    final ZeebeClient client = builder.build();
    clientCreationCallback.accept(client);

    return client;
  }

  @Override
  public URI getZeebeGrpcAddress() {
    return zeebeContainer.getGrpcApiAddress();
  }

  @Override
  public URI getZeebeRestAddress() {
    return zeebeContainer.getRestApiAddress();
  }
}
