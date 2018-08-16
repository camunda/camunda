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
package io.zeebe.gateway.util;

import io.zeebe.gateway.ZeebeClient;
import io.zeebe.gateway.ZeebeClientBuilder;
import io.zeebe.gateway.api.clients.JobClient;
import io.zeebe.gateway.api.clients.TopicClient;
import io.zeebe.gateway.api.clients.WorkflowClient;
import io.zeebe.gateway.impl.ZeebeClientBuilderImpl;
import io.zeebe.gateway.impl.ZeebeClientImpl;
import io.zeebe.test.broker.protocol.brokerapi.StubBrokerRule;
import io.zeebe.util.sched.clock.ControlledActorClock;
import java.util.function.Consumer;
import org.junit.rules.ExternalResource;

public class ClientRule extends ExternalResource {

  public static final int DEFAULT_PARTITION = 1;

  protected final Consumer<ZeebeClientBuilder> configurator;

  protected ZeebeClient client;
  protected ControlledActorClock clock;

  public ClientRule(StubBrokerRule brokerRule) {
    this(brokerRule, c -> {});
  }

  public ClientRule(StubBrokerRule brokerRule, Consumer<ZeebeClientBuilder> configurator) {
    this.configurator =
        config -> {
          config.brokerContactPoint(brokerRule.getSocketAddress().toString());
          configurator.accept(config);
        };
  }

  @Override
  protected void before() throws Throwable {
    clock = new ControlledActorClock();
    final ZeebeClientBuilderImpl builder = (ZeebeClientBuilderImpl) ZeebeClient.newClientBuilder();
    configurator.accept(builder);

    client = new ZeebeClientImpl(builder, clock);
  }

  public ControlledActorClock getClock() {
    return clock;
  }

  @Override
  protected void after() {
    client.close();
    clock.reset();
  }

  public ZeebeClient getClient() {
    return client;
  }

  public WorkflowClient workflowClient() {
    return client.topicClient().workflowClient();
  }

  public JobClient jobClient() {
    return client.topicClient().jobClient();
  }

  public TopicClient topicClient() {
    return client.topicClient();
  }

  public String getDefaultTopicName() {
    return StubBrokerRule.TEST_TOPIC_NAME;
  }

  public int getDefaultPartitionId() {
    return StubBrokerRule.TEST_PARTITION_ID;
  }
}
