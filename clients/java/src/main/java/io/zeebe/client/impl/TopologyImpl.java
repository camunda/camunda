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
package io.zeebe.client.impl;

import io.zeebe.client.api.commands.BrokerInfo;
import io.zeebe.client.api.commands.Topology;
import io.zeebe.gateway.protocol.GatewayOuterClass.TopologyResponse;
import java.util.List;
import java.util.stream.Collectors;

public class TopologyImpl implements Topology {

  private final List<BrokerInfo> brokers;
  private final int clusterSize;
  private final int partitionsCount;
  private final int replicationFactor;

  public TopologyImpl(final TopologyResponse response) {
    brokers =
        response.getBrokersList().stream().map(BrokerInfoImpl::new).collect(Collectors.toList());
    clusterSize = response.getClusterSize();
    partitionsCount = response.getPartitionsCount();
    replicationFactor = response.getReplicationFactor();
  }

  @Override
  public List<BrokerInfo> getBrokers() {
    return brokers;
  }

  @Override
  public int getClusterSize() {
    return clusterSize;
  }

  @Override
  public int getPartitionsCount() {
    return partitionsCount;
  }

  @Override
  public int getReplicationFactor() {
    return replicationFactor;
  }

  @Override
  public String toString() {
    return "TopologyImpl{"
        + "brokers="
        + brokers
        + ", clusterSize="
        + clusterSize
        + ", partitionsCount="
        + partitionsCount
        + ", replicationFactor="
        + replicationFactor
        + '}';
  }
}
