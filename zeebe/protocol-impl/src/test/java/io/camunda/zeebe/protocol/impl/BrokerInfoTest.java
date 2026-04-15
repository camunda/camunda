/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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

    // then — the int node ID round-trips correctly; no region was set
    assertThat(decoded.getLocalNodeId()).isEqualTo(nodeId);
    assertThat(decoded.getNodeId()).isEqualTo(String.valueOf(nodeId));
    assertThat(decoded.getRegion()).isNull();
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
    assertThat(decoded.getLocalNodeId()).isEqualTo(nodeId);
    assertThat(decoded.getNodeId()).isEqualTo(String.valueOf(nodeId));
    assertThat(decoded.getPartitionsCount()).isEqualTo(partitionsCount);
    assertThat(decoded.getClusterSize()).isEqualTo(clusterSize);
    assertThat(decoded.getReplicationFactor()).isEqualTo(replicationFactor);
    assertThat(decoded.getAddresses()).isEmpty();
    assertThat(decoded.getPartitionRoles()).isEmpty();
    assertThat(decoded.getPartitionHealthStatuses()).isEmpty();
  }

  @Test
  void shouldThrowOnUnsetFields() {
    // given
    final BrokerInfo brokerInfo = new BrokerInfo();

    // then
    assertThatIllegalStateException()
        .isThrownBy(brokerInfo::getNodeId)
        .withMessageContaining("nodeId");
    assertThatIllegalStateException()
        .isThrownBy(brokerInfo::getPartitionsCount)
        .withMessageContaining("partitionsCount");
    assertThatIllegalStateException()
        .isThrownBy(brokerInfo::getClusterSize)
        .withMessageContaining("clusterSize");
    assertThatIllegalStateException()
        .isThrownBy(brokerInfo::getReplicationFactor)
        .withMessageContaining("replicationFactor");
    assertThat(brokerInfo.getAddresses()).isEmpty();
    assertThat(brokerInfo.getPartitionRoles()).isEmpty();
    assertThat(brokerInfo.getPartitionHealthStatuses()).isEmpty();
  }

  @Test
  void shouldEncodeDecodeBrokerInfoWithRegion() {
    // given — a region-aware broker with composite node ID "us-east1-0"
    final int localNodeId = 0;
    final String region = "us-east1";

    final BrokerInfo brokerInfo =
        new BrokerInfo()
            .setNodeId(localNodeId)
            .setRegion(region)
            .setPartitionsCount(3)
            .setClusterSize(4)
            .setReplicationFactor(4);

    // when
    final var decoded = encodeDecode(brokerInfo);

    // then — the composite node ID is composed from region + local node ID
    assertThat(decoded.getLocalNodeId()).isEqualTo(localNodeId);
    assertThat(decoded.getRegion()).isEqualTo(region);
    assertThat(decoded.getNodeId()).isEqualTo(region + "-" + localNodeId);
  }

  @Test
  void shouldComposeCompositeNodeIdFromRegionAndLocalId() {
    // given — two brokers in different regions with the same local node ID (0)
    final BrokerInfo eastBroker =
        new BrokerInfo()
            .setNodeId(0)
            .setRegion("us-east1")
            .setPartitionsCount(1)
            .setClusterSize(2)
            .setReplicationFactor(1);
    final BrokerInfo westBroker =
        new BrokerInfo()
            .setNodeId(0)
            .setRegion("us-west1")
            .setPartitionsCount(1)
            .setClusterSize(2)
            .setReplicationFactor(1);

    // when
    final var decodedEast = encodeDecode(eastBroker);
    final var decodedWest = encodeDecode(westBroker);

    // then — composite node IDs are globally unique even though local IDs are the same
    assertThat(decodedEast.getNodeId()).isEqualTo("us-east1-0");
    assertThat(decodedWest.getNodeId()).isEqualTo("us-west1-0");
    assertThat(decodedEast.getNodeId()).isNotEqualTo(decodedWest.getNodeId());
    assertThat(decodedEast.getLocalNodeId()).isEqualTo(decodedWest.getLocalNodeId());
  }

  @Test
  void shouldParseCompositeNodeIdViaSetNodeIdString() {
    // given — composite string set via setNodeId(String)
    final BrokerInfo brokerInfo =
        new BrokerInfo()
            .setNodeId("us-east1-2")
            .setPartitionsCount(1)
            .setClusterSize(2)
            .setReplicationFactor(1);

    // when
    final var decoded = encodeDecode(brokerInfo);

    // then
    assertThat(decoded.getNodeId()).isEqualTo("us-east1-2");
    assertThat(decoded.getLocalNodeId()).isEqualTo(2);
    assertThat(decoded.getRegion()).isEqualTo("us-east1");
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
