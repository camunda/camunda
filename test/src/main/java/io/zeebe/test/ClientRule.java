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
package io.zeebe.test;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.commands.BrokerInfo;
import io.zeebe.client.api.commands.PartitionInfo;
import io.zeebe.client.api.commands.Topology;
import java.util.List;
import java.util.Properties;
import java.util.function.Supplier;
import org.junit.rules.ExternalResource;

public class ClientRule extends ExternalResource {

  protected int defaultPartition;

  protected ZeebeClient client;

  protected final Supplier<Properties> properties;

  public ClientRule() {
    this(Properties::new);
  }

  public ClientRule(final Supplier<Properties> propertiesProvider) {
    this.properties = propertiesProvider;
  }

  public ZeebeClient getClient() {
    return client;
  }

  public int getDefaultPartition() {
    return defaultPartition;
  }

  @Override
  protected void before() {
    createClient();
  }

  public void createClient() {
    client = ZeebeClient.newClientBuilder().withProperties(properties.get()).build();
    determineDefaultPartition();
  }

  private void determineDefaultPartition() {
    final Topology topology = client.newTopologyRequest().send().join();

    defaultPartition = -1;
    final List<BrokerInfo> topologyBrokers = topology.getBrokers();

    for (final BrokerInfo leader : topologyBrokers) {
      final List<PartitionInfo> partitions = leader.getPartitions();
      for (final PartitionInfo brokerPartitionState : partitions) {
        if (brokerPartitionState.isLeader()) {
          defaultPartition = brokerPartitionState.getPartitionId();
          break;
        }
      }
    }

    if (defaultPartition < 0) {
      throw new RuntimeException("Could not detect leader for default partition");
    }
  }

  @Override
  protected void after() {
    destroyClient();
  }

  public void destroyClient() {
    client.close();
    client = null;
  }
}
