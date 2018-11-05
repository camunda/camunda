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
package io.zeebe.protocol.impl.data.cluster;

import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.msgpack.property.ArrayProperty;
import io.zeebe.msgpack.property.EnumProperty;
import io.zeebe.msgpack.property.IntegerProperty;
import io.zeebe.msgpack.property.StringProperty;
import io.zeebe.msgpack.value.ValueArray;
import io.zeebe.protocol.PartitionState;
import org.agrona.DirectBuffer;

/** Message pack formatted DTO of topology; can be requested through client api. */
public class TopologyResponseDto extends UnpackedObject {
  private final ArrayProperty<BrokerDto> brokersProp =
      new ArrayProperty<>("brokers", new BrokerDto());
  private final IntegerProperty clusterSizeProp = new IntegerProperty("clusterSize");
  private final IntegerProperty partitionsCountProp = new IntegerProperty("partitionsCount");
  private final IntegerProperty replicationFactorProp = new IntegerProperty("replicationFactor");

  public TopologyResponseDto() {
    this.declareProperty(brokersProp)
        .declareProperty(clusterSizeProp)
        .declareProperty(partitionsCountProp)
        .declareProperty(replicationFactorProp);
  }

  public ArrayProperty<BrokerDto> brokers() {
    return brokersProp;
  }

  public int getClusterSize() {
    return clusterSizeProp.getValue();
  }

  public TopologyResponseDto setClusterSize(int clusterSize) {
    this.clusterSizeProp.setValue(clusterSize);
    return this;
  }

  public int getPartitionsCount() {
    return partitionsCountProp.getValue();
  }

  public TopologyResponseDto setPartitionsCount(int partitionsCount) {
    this.partitionsCountProp.setValue(partitionsCount);
    return this;
  }

  public int getReplicationFactor() {
    return replicationFactorProp.getValue();
  }

  public TopologyResponseDto setReplicationFactor(int replicationFactor) {
    replicationFactorProp.setValue(replicationFactor);
    return this;
  }

  public static class BrokerDto extends UnpackedObject {
    private final IntegerProperty nodeIdProp = new IntegerProperty("nodeId");
    private final StringProperty hostProp = new StringProperty("host");
    private final IntegerProperty portProp = new IntegerProperty("port");

    private final ArrayProperty<PartitionDto> partitionStatesProp =
        new ArrayProperty<>("partitions", new PartitionDto());

    public BrokerDto() {
      this.declareProperty(nodeIdProp)
          .declareProperty(hostProp)
          .declareProperty(portProp)
          .declareProperty(partitionStatesProp);
    }

    public int getNodeId() {
      return nodeIdProp.getValue();
    }

    public BrokerDto setNodeId(int nodeId) {
      nodeIdProp.setValue(nodeId);
      return this;
    }

    public DirectBuffer getHost() {
      return hostProp.getValue();
    }

    public BrokerDto setHost(String host) {
      this.hostProp.setValue(host);
      return this;
    }

    public BrokerDto setHost(final DirectBuffer host, final int offset, final int length) {
      this.hostProp.setValue(host, offset, length);
      return this;
    }

    public int getPort() {
      return portProp.getValue();
    }

    public BrokerDto setPort(final int port) {
      portProp.setValue(port);
      return this;
    }

    public ValueArray<PartitionDto> partitionStates() {
      return partitionStatesProp;
    }
  }

  public static class PartitionDto extends UnpackedObject {
    private final EnumProperty<PartitionState> stateProp =
        new EnumProperty<>("state", PartitionState.class);
    private final IntegerProperty partitionIdProp = new IntegerProperty("partitionId");
    private final IntegerProperty replicationFactorProp = new IntegerProperty("replicationFactor");

    public PartitionDto() {
      this.declareProperty(stateProp)
          .declareProperty(partitionIdProp)
          .declareProperty(replicationFactorProp);
    }

    public PartitionState getState() {
      return stateProp.getValue();
    }

    public PartitionDto setState(PartitionState state) {
      this.stateProp.setValue(state);
      return this;
    }

    public boolean isLeader() {
      return getState() == PartitionState.LEADER;
    }

    public int getPartitionId() {
      return partitionIdProp.getValue();
    }

    public PartitionDto setPartitionId(final int partitionId) {
      partitionIdProp.setValue(partitionId);
      return this;
    }

    public int getReplicationFactor() {
      return replicationFactorProp.getValue();
    }

    public PartitionDto setReplicationFactor(int replicationFactor) {
      replicationFactorProp.setValue(replicationFactor);
      return this;
    }
  }
}
