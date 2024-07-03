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
package io.camunda.client.impl.response;

import io.camunda.client.api.response.BrokerInfo;
import io.camunda.client.api.response.Topology;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.TopologyResponse;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public final class TopologyImpl implements Topology {

  private final List<BrokerInfo> brokers;
  private final int clusterSize;
  private final int partitionsCount;
  private final int replicationFactor;
  private final String gatewayVersion;

  public TopologyImpl(final TopologyResponse grpcResponse) {
    brokers =
        grpcResponse.getBrokersList().stream()
            .map(BrokerInfoImpl::new)
            .collect(Collectors.toList());
    clusterSize = grpcResponse.getClusterSize();
    partitionsCount = grpcResponse.getPartitionsCount();
    replicationFactor = grpcResponse.getReplicationFactor();
    gatewayVersion = grpcResponse.getGatewayVersion();
  }

  public TopologyImpl(final io.camunda.client.protocol.rest.TopologyResponse httpResponse) {
    brokers =
        Optional.ofNullable(httpResponse.getBrokers()).orElse(Collections.emptyList()).stream()
            .map(BrokerInfoImpl::new)
            .collect(Collectors.toList());
    clusterSize = httpResponse.getClusterSize() == null ? 0 : httpResponse.getClusterSize();
    partitionsCount =
        httpResponse.getPartitionsCount() == null ? 0 : httpResponse.getPartitionsCount();
    replicationFactor =
        httpResponse.getReplicationFactor() == null ? 0 : httpResponse.getReplicationFactor();
    gatewayVersion =
        httpResponse.getGatewayVersion() == null ? "" : httpResponse.getGatewayVersion();
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
  public String getGatewayVersion() {
    return gatewayVersion;
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
        + ", gatewayVersion="
        + gatewayVersion
        + '}';
  }
}
