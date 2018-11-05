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
package io.zeebe.gateway.api;

import io.zeebe.gateway.api.util.StubbedGateway;
import io.zeebe.gateway.api.util.StubbedGateway.RequestStub;
import io.zeebe.gateway.impl.broker.request.BrokerTopologyRequest;
import io.zeebe.gateway.impl.broker.response.BrokerResponse;
import io.zeebe.msgpack.value.ValueArray;
import io.zeebe.protocol.PartitionState;
import io.zeebe.protocol.impl.data.cluster.TopologyResponseDto;
import io.zeebe.protocol.impl.data.cluster.TopologyResponseDto.PartitionDto;

public class RequestTopologyStub
    implements RequestStub<BrokerTopologyRequest, BrokerResponse<TopologyResponseDto>> {

  @Override
  public void registerWith(StubbedGateway gateway) {
    gateway.registerHandler(BrokerTopologyRequest.class, this);
  }

  public int getClusterSize() {
    return 5;
  }

  public int getPartitionsCount() {
    return 12;
  }

  public int getReplicationFactor() {
    return 3;
  }

  @Override
  public BrokerResponse<TopologyResponseDto> handle(BrokerTopologyRequest request) {
    final TopologyResponseDto response = new TopologyResponseDto();
    response.setClusterSize(getClusterSize());
    response.setPartitionsCount(getPartitionsCount());
    response.setReplicationFactor(getReplicationFactor());

    ValueArray<PartitionDto> partitions =
        response.brokers().add().setNodeId(0).setPort(1234).setHost("foo").partitionStates();
    partitions
        .add()
        .setState(PartitionState.LEADER)
        .setReplicationFactor(getReplicationFactor())
        .setPartitionId(0);
    partitions
        .add()
        .setState(PartitionState.FOLLOWER)
        .setReplicationFactor(getReplicationFactor())
        .setPartitionId(1);

    partitions =
        response.brokers().add().setNodeId(1).setPort(5678).setHost("bar").partitionStates();
    partitions
        .add()
        .setState(PartitionState.FOLLOWER)
        .setReplicationFactor(getReplicationFactor())
        .setPartitionId(0);
    partitions
        .add()
        .setState(PartitionState.LEADER)
        .setReplicationFactor(getReplicationFactor())
        .setPartitionId(1);

    return new BrokerResponse<>(response);
  }
}
