/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.clustering.base.topology;

import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.msgpack.property.*;
import io.zeebe.msgpack.value.ValueArray;
import io.zeebe.raft.state.RaftState;
import org.agrona.DirectBuffer;

/** Message pack formatted DTO of topology; can be requested through client api. */
public class TopologyDto extends UnpackedObject {
  private final ArrayProperty<BrokerDto> brokersProp =
      new ArrayProperty<>("brokers", new BrokerDto());

  public TopologyDto() {
    this.declareProperty(brokersProp);
  }

  public ArrayProperty<BrokerDto> brokers() {
    return brokersProp;
  }

  public static class BrokerDto extends UnpackedObject {
    private final StringProperty hostProp = new StringProperty("host");
    private final IntegerProperty portProp = new IntegerProperty("port");

    private final ArrayProperty<PartitionDto> partitionStatesProp =
        new ArrayProperty<>("partitions", new PartitionDto());

    public BrokerDto() {
      this.declareProperty(hostProp).declareProperty(portProp).declareProperty(partitionStatesProp);
    }

    public DirectBuffer getHost() {
      return hostProp.getValue();
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
    private final EnumProperty<RaftState> stateProp = new EnumProperty<>("state", RaftState.class);
    private final StringProperty topicNameProp = new StringProperty("topicName");
    private final IntegerProperty partitionIdProp = new IntegerProperty("partitionId");
    private final IntegerProperty replicationFactorProp = new IntegerProperty("replicationFactor");

    public PartitionDto() {
      this.declareProperty(stateProp)
          .declareProperty(topicNameProp)
          .declareProperty(partitionIdProp)
          .declareProperty(replicationFactorProp);
    }

    public RaftState getState() {
      return stateProp.getValue();
    }

    public PartitionDto setState(RaftState eventType) {
      this.stateProp.setValue(eventType);
      return this;
    }

    public PartitionDto setTopicName(
        final DirectBuffer topicName, final int offset, final int length) {
      this.topicNameProp.setValue(topicName, offset, length);
      return this;
    }

    public int getPartitionId() {
      return partitionIdProp.getValue();
    }

    public int getReplicationFactor() {
      return replicationFactorProp.getValue();
    }

    public PartitionDto setReplicationFactor(int replicationFactor) {
      replicationFactorProp.setValue(replicationFactor);
      return this;
    }

    public PartitionDto setPartitionId(final int partitionId) {
      partitionIdProp.setValue(partitionId);
      return this;
    }
  }
}
