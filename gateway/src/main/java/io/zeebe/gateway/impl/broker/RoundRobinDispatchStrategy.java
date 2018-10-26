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
package io.zeebe.gateway.impl.broker;

import io.zeebe.gateway.impl.broker.cluster.BrokerClusterState;
import io.zeebe.gateway.impl.broker.cluster.BrokerTopologyManagerImpl;
import java.util.concurrent.atomic.AtomicInteger;

public class RoundRobinDispatchStrategy implements RequestDispatchStrategy {

  protected final BrokerTopologyManagerImpl topologyManager;
  protected final AtomicInteger partitions = new AtomicInteger(0);

  public RoundRobinDispatchStrategy(final BrokerTopologyManagerImpl topologyManager) {
    this.topologyManager = topologyManager;
  }

  @Override
  public int determinePartition() {
    final BrokerClusterState topology = topologyManager.getTopology();

    if (topology != null) {
      final int offset = partitions.getAndIncrement();
      return topology.getPartition(offset);
    } else {
      return BrokerClusterState.PARTITION_ID_NULL;
    }
  }
}
