/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.impl;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import io.camunda.zeebe.protocol.impl.encoding.BrokerInfo;
import io.camunda.zeebe.protocol.record.PartitionHealthStatus;
import io.camunda.zeebe.protocol.record.PartitionRole;
import java.util.HashMap;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

final class BrokerInfoTest {

  @Test
  void shouldEncodeDecodeBrokerInfo() {
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
    final Map<Integer, PartitionHealthStatus> partitionHealthStatuses = new HashMap<>();
    partitionHealthStatuses.put(1, PartitionHealthStatus.HEALTHY);
    partitionHealthStatuses.put(2, PartitionHealthStatus.UNHEALTHY);
    partitionHealthStatuses.put(123, PartitionHealthStatus.HEALTHY);

    final BrokerInfo brokerInfo =
        new BrokerInfo()
            .setNodeId(nodeId)
            .setPartitionsCount(partitionsCount)
            .setClusterSize(clusterSize)
            .setReplicationFactor(replicationFactor);

    addresses.forEach(brokerInfo::addAddress);
    partitionRoles.forEach(brokerInfo::addPartitionRole);
    partitionHealthStatuses.forEach(brokerInfo::addPartitionHealth);

    // when
    final var decoded = encodeDecode(brokerInfo);

    // then
    assertThat(decoded.getNodeId()).isEqualTo(nodeId);
    assertThat(decoded.getPartitionsCount()).isEqualTo(partitionsCount);
    assertThat(decoded.getClusterSize()).isEqualTo(clusterSize);
    assertThat(decoded.getReplicationFactor()).isEqualTo(replicationFactor);
    assertThat(decoded.getAddresses()).containsAllEntriesOf(addresses);
    assertThat(decoded.getPartitionRoles()).containsAllEntriesOf(partitionRoles);
    assertThat(decoded.getPartitionHealthStatuses()).containsAllEntriesOf(partitionHealthStatuses);
  }

  @Test
  void shouldEncodeDecodeBrokerInfoWithEmptyMaps() {
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
    final var decoded = encodeDecode(brokerInfo);

    // then
    assertThat(decoded.getNodeId()).isEqualTo(nodeId);
    assertThat(decoded.getPartitionsCount()).isEqualTo(partitionsCount);
    assertThat(decoded.getClusterSize()).isEqualTo(clusterSize);
    assertThat(decoded.getReplicationFactor()).isEqualTo(replicationFactor);
    assertThat(decoded.getAddresses()).isEmpty();
    assertThat(decoded.getPartitionRoles()).isEmpty();
    assertThat(decoded.getPartitionHealthStatuses()).isEmpty();
  }

  @Test
  void shouldEncodeDecodeNullValues() {
    // given
    final BrokerInfo brokerInfo = new BrokerInfo();

    // when
    final var decoded = encodeDecode(brokerInfo);

    // then
    assertThatIllegalStateException()
        .isThrownBy(decoded::getNodeId)
        .withMessageContaining("nodeId");
    assertThatIllegalStateException()
        .isThrownBy(decoded::getPartitionsCount)
        .withMessageContaining("partitionsCount");
    assertThatIllegalStateException()
        .isThrownBy(decoded::getClusterSize)
        .withMessageContaining("clusterSize");
    assertThatIllegalStateException()
        .isThrownBy(decoded::getReplicationFactor)
        .withMessageContaining("replicationFactor");
    assertThat(decoded.getAddresses()).isEmpty();
    assertThat(decoded.getPartitionRoles()).isEmpty();
    assertThat(decoded.getPartitionHealthStatuses()).isEmpty();
  }

  private BrokerInfo encodeDecode(final BrokerInfo brokerInfo) {
    // encode
    final UnsafeBuffer buffer = new UnsafeBuffer(new byte[brokerInfo.getLength()]);
    brokerInfo.write(buffer, 0);

    final var decoded = new BrokerInfo();
    // decode
    decoded.wrap(buffer, 0, buffer.capacity());
    return decoded;
  }
}
