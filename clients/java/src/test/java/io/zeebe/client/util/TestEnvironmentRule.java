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

import io.zeebe.broker.Broker;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.ZeebeClientBuilder;
import java.util.function.Consumer;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class TestEnvironmentRule extends TestWatcher {

  private final EmbeddedBrokerRule brokerRule;
  private final ClientRule clientRule;

  public TestEnvironmentRule() {
    this(b -> {});
  }

  public TestEnvironmentRule(final Consumer<ZeebeClientBuilder> clientConfigurator) {
    this(clientConfigurator, c -> {});
  }

  public TestEnvironmentRule(
      final Consumer<ZeebeClientBuilder> clientConfigurator,
      final Consumer<BrokerCfg> brokerConfigurator) {
    this.brokerRule = new EmbeddedBrokerRule(brokerConfigurator);
    this.clientRule = new ClientRule(brokerRule, clientConfigurator);
  }

  @Override
  public Statement apply(Statement base, Description description) {
    final Statement statement = clientRule.apply(base, description);
    return brokerRule.apply(statement, description);
  }

  public ClientRule getClientRule() {
    return clientRule;
  }

  public ZeebeClient getClient() {
    return clientRule.getClient();
  }

  public EmbeddedBrokerRule getBrokerRule() {
    return brokerRule;
  }

  public Broker getBroker() {
    return brokerRule.getBroker();
  }
}
