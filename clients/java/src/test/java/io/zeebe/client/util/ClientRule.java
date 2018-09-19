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
package io.zeebe.client.util;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.ZeebeClientBuilder;
import io.zeebe.transport.SocketAddress;
import java.util.function.Consumer;
import org.junit.rules.ExternalResource;

public class ClientRule extends ExternalResource {

  private final EmbeddedBrokerRule embeddedBrokerRule;
  private final Consumer<ZeebeClientBuilder> configurator;

  private ZeebeClient zeebeClient;

  public ClientRule(final EmbeddedBrokerRule embeddedBrokerRule) {
    this(embeddedBrokerRule, builder -> {});
  }

  public ClientRule(
      final EmbeddedBrokerRule embeddedBrokerRule,
      final Consumer<ZeebeClientBuilder> configurator) {
    this.embeddedBrokerRule = embeddedBrokerRule;
    this.configurator = configurator;
  }

  public ZeebeClient getClient() {
    return zeebeClient;
  }

  @Override
  protected void before() {
    final SocketAddress gatewayAddress = embeddedBrokerRule.getGatewayAddress();

    final ZeebeClientBuilder builder = ZeebeClient.newClientBuilder();
    configurator.accept(builder);
    builder.brokerContactPoint(gatewayAddress.toString());

    zeebeClient = builder.build();
  }

  @Override
  protected void after() {
    zeebeClient.close();
  }
}
