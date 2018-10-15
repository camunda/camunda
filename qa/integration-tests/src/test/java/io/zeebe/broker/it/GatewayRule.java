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
package io.zeebe.broker.it;

import static io.zeebe.test.util.TestUtil.doRepeatedly;
import static org.junit.Assert.fail;

import io.zeebe.broker.it.clustering.ClusteringRule;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.gateway.ZeebeClient;
import io.zeebe.gateway.ZeebeClientBuilder;
import io.zeebe.gateway.api.clients.JobClient;
import io.zeebe.gateway.api.clients.WorkflowClient;
import io.zeebe.gateway.api.commands.PartitionInfo;
import io.zeebe.gateway.api.commands.Topology;
import io.zeebe.gateway.api.record.ValueType;
import io.zeebe.gateway.impl.ZeebeClientBuilderImpl;
import io.zeebe.gateway.impl.ZeebeClientImpl;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.intent.DeploymentIntent;
import io.zeebe.transport.ClientTransport;
import io.zeebe.util.sched.clock.ControlledActorClock;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.rules.ExternalResource;

public class GatewayRule extends ExternalResource {

  private final Consumer<ZeebeClientBuilder> configurator;

  protected ZeebeClient client;
  private final ControlledActorClock actorClock = new ControlledActorClock();

  public GatewayRule(final EmbeddedBrokerRule brokerRule) {
    this(brokerRule, config -> {});
  }

  public GatewayRule(
      final EmbeddedBrokerRule brokerRule, final Consumer<ZeebeClientBuilder> configurator) {
    this(
        config -> {
          config.brokerContactPoint(brokerRule.getClientAddress().toString());
          configurator.accept(config);
        });
  }

  public GatewayRule(final ClusteringRule clusteringRule) {
    this(config -> config.brokerContactPoint(clusteringRule.getClientAddress().toString()));
  }

  private GatewayRule(final Consumer<ZeebeClientBuilder> configurator) {
    this.configurator = configurator;
  }

  @Override
  protected void before() {
    final ZeebeClientBuilderImpl builder = (ZeebeClientBuilderImpl) ZeebeClient.newClientBuilder();
    configurator.accept(builder);
    client = builder.setActorClock(actorClock).build();
  }

  @Override
  protected void after() {
    client.close();
    client = null;
  }

  public ZeebeClient getClient() {
    return client;
  }

  public void interruptBrokerConnections() {
    final ClientTransport transport = ((ZeebeClientImpl) client).getTransport();
    transport.interruptAllChannels();
  }

  public void waitUntilDeploymentIsDone(final long key) {
    final AtomicBoolean deploymentFound = new AtomicBoolean(false);

    client
        .newSubscription()
        .name("deployment-await")
        .recordHandler(
            record -> {
              if (record.getMetadata().getPartitionId() == Protocol.DEPLOYMENT_PARTITION
                  && record.getMetadata().getValueType() == ValueType.DEPLOYMENT
                  && record.getMetadata().getIntent().equals(DeploymentIntent.CREATED.name())
                  && record.getKey() == key) {
                deploymentFound.compareAndSet(false, true);
              }
            })
        .open();

    doRepeatedly(
            () -> {
              try {
                Thread.sleep(100);
              } catch (Exception ex) {
                fail();
              }
            })
        .until((v) -> deploymentFound.get());
  }

  public List<Integer> getPartitions() {
    final Topology topology = client.newTopologyRequest().send().join();

    return topology
        .getBrokers()
        .stream()
        .flatMap(i -> i.getPartitions().stream())
        .filter(PartitionInfo::isLeader)
        .map(PartitionInfo::getPartitionId)
        .collect(Collectors.toList());
  }

  public ControlledActorClock getActorClock() {
    return actorClock;
  }

  public WorkflowClient getWorkflowClient() {
    return getClient().workflowClient();
  }

  public JobClient getJobClient() {
    return getClient().jobClient();
  }
}
