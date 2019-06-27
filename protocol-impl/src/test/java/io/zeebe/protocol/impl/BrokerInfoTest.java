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
package io.zeebe.protocol.impl;

import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.protocol.impl.encoding.BrokerInfo;
import io.zeebe.protocol.record.BrokerInfoEncoder;
import io.zeebe.protocol.record.PartitionRole;
import java.util.HashMap;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

public class BrokerInfoTest {

  @Test
  public void shouldEncodeDecodeBrokerInfo() {
    // given
    final int nodeId = 123;
    final int partitionsCount = 345;
    final int clusterSize = 567;
    final int replicationFactor = 789;
    final Map<DirectBuffer, DirectBuffer> addresses = new HashMap<>();
    addresses.put(wrapString("foo"), wrapString("192.159.12.1:23"));
    addresses.put(wrapString("bar"), wrapString("zeebe-0.cluster.loc:12312"));
    final Map<Integer, PartitionRole> partitionRoles = new HashMap<>();
    partitionRoles.put(1, PartitionRole.FOLLOWER);
    partitionRoles.put(2, PartitionRole.LEADER);
    partitionRoles.put(231, PartitionRole.FOLLOWER);

    final BrokerInfo brokerInfo =
        new BrokerInfo()
            .setNodeId(nodeId)
            .setPartitionsCount(partitionsCount)
            .setClusterSize(clusterSize)
            .setReplicationFactor(replicationFactor);

    addresses.forEach(brokerInfo::addAddress);
    partitionRoles.forEach(brokerInfo::addPartitionRole);

    // when
    encodeDecode(brokerInfo);

    // then
    assertThat(brokerInfo.getNodeId()).isEqualTo(nodeId);
    assertThat(brokerInfo.getPartitionsCount()).isEqualTo(partitionsCount);
    assertThat(brokerInfo.getClusterSize()).isEqualTo(clusterSize);
    assertThat(brokerInfo.getReplicationFactor()).isEqualTo(replicationFactor);
    assertThat(brokerInfo.getAddresses()).containsAllEntriesOf(addresses);
  }

  @Test
  public void shouldEncodeDecodeBrokerInfoWithEmptyMaps() {
    // given
    final int nodeId = 123;
    final int partitionsCount = 345;
    final int clusterSize = 567;
    final int replicationFactor = 789;

    final BrokerInfo brokerInfo =
        new BrokerInfo()
            .setNodeId(nodeId)
            .setPartitionsCount(partitionsCount)
            .setClusterSize(clusterSize)
            .setReplicationFactor(replicationFactor);

    // when
    encodeDecode(brokerInfo);

    // then
    assertThat(brokerInfo.getNodeId()).isEqualTo(nodeId);
    assertThat(brokerInfo.getPartitionsCount()).isEqualTo(partitionsCount);
    assertThat(brokerInfo.getClusterSize()).isEqualTo(clusterSize);
    assertThat(brokerInfo.getReplicationFactor()).isEqualTo(replicationFactor);
    assertThat(brokerInfo.getAddresses()).isEmpty();
    assertThat(brokerInfo.getPartitionRoles()).isEmpty();
  }

  @Test
  public void shouldEncodeDecodeNullValues() {
    // given
    final BrokerInfo brokerInfo = new BrokerInfo();

    // when
    encodeDecode(brokerInfo);

    // then
    assertThat(brokerInfo.getNodeId()).isEqualTo(BrokerInfoEncoder.nodeIdNullValue());
    assertThat(brokerInfo.getPartitionsCount())
        .isEqualTo(BrokerInfoEncoder.partitionsCountNullValue());
    assertThat(brokerInfo.getClusterSize()).isEqualTo(BrokerInfoEncoder.clusterSizeNullValue());
    assertThat(brokerInfo.getReplicationFactor())
        .isEqualTo(BrokerInfoEncoder.replicationFactorNullValue());
    assertThat(brokerInfo.getAddresses()).isEmpty();
    assertThat(brokerInfo.getPartitionRoles()).isEmpty();
  }

  private void encodeDecode(BrokerInfo brokerInfo) {
    // encode
    final UnsafeBuffer buffer = new UnsafeBuffer(new byte[brokerInfo.getLength()]);
    brokerInfo.write(buffer, 0);

    // decode
    brokerInfo.reset();
    brokerInfo.wrap(buffer, 0, buffer.capacity());
  }
}
