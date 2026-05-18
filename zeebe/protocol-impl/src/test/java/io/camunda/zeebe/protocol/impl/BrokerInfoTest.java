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
import static org.assertj.core.api.Assertions.assertThatNoException;

import io.camunda.zeebe.protocol.impl.encoding.BrokerInfo;
import io.camunda.zeebe.protocol.record.BrokerInfoEncoder;
import io.camunda.zeebe.protocol.record.MessageHeaderEncoder;
import io.camunda.zeebe.protocol.record.PartitionHealthStatus;
import io.camunda.zeebe.protocol.record.PartitionRole;
import java.util.HashMap;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
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
            .setBrokerId(nodeId, "eu-west-1b")
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
    assertThat(decoded.getZone()).isEqualTo("eu-west-1b");
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
            .setBrokerId(nodeId, null)
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
    assertThat(decoded.getZone()).isNull();
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
    assertThat(decoded.getZone()).isNull();
  }

  @Test
  void shouldDecodeVersion8PayloadWithVersion7Header() {
    // given -- manually encode a v7 BrokerInfo payload (without zone)
    // This simulates the forward compat scenario: a broker on the previous version receives
    // a payload encoded without zone. BrokerInfo.wrap() should handle it gracefully.
    final int nodeId = 42;
    final int partitionsCount = 3;
    final int clusterSize = 3;
    final int replicationFactor = 3;
    final String versionStr = "8.7.0";
    final byte[] versionBytes = versionStr.getBytes(java.nio.charset.StandardCharsets.UTF_8);

    final MutableDirectBuffer buffer = new UnsafeBuffer(new byte[1024]);
    int offset = 0;

    // write header with version=7
    final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    headerEncoder.wrap(buffer, offset);
    headerEncoder
        .blockLength(BrokerInfoEncoder.BLOCK_LENGTH)
        .templateId(BrokerInfoEncoder.TEMPLATE_ID)
        .schemaId(BrokerInfoEncoder.SCHEMA_ID)
        .version(7);
    offset += MessageHeaderEncoder.ENCODED_LENGTH;

    // write body with all v7 fields
    final BrokerInfoEncoder bodyEncoder = new BrokerInfoEncoder();
    bodyEncoder
        .wrap(buffer, offset)
        .nodeId(nodeId)
        .partitionsCount(partitionsCount)
        .clusterSize(clusterSize)
        .replicationFactor(replicationFactor);

    bodyEncoder.addressesCount(0);
    bodyEncoder.partitionRolesCount(0);
    bodyEncoder.partitionLeaderTermsCount(0);
    bodyEncoder.putVersion(versionBytes, 0, versionBytes.length);
    bodyEncoder.partitionHealthCount(0);

    // zone is NOT written (this is a v7 payload)

    final int totalLength = MessageHeaderEncoder.ENCODED_LENGTH + bodyEncoder.encodedLength();

    // when -- decode with BrokerInfo directly
    final BrokerInfo decoded = new BrokerInfo();
    assertThatNoException().isThrownBy(() -> decoded.wrap(buffer, 0, totalLength));

    // then -- all v7 fields decode correctly, zone is null
    assertThat(decoded.getNodeId()).isEqualTo(nodeId);
    assertThat(decoded.getPartitionsCount()).isEqualTo(partitionsCount);
    assertThat(decoded.getClusterSize()).isEqualTo(clusterSize);
    assertThat(decoded.getReplicationFactor()).isEqualTo(replicationFactor);
    assertThat(decoded.getVersion()).isEqualTo(versionStr);
    assertThat(decoded.getZone()).isNull();
  }

  @Test
  void shouldDecodeVersion7PayloadWithoutZone() {
    // given — manually encode a v7 BrokerInfo payload (no zone field)
    final int nodeId = 42;
    final int partitionsCount = 3;
    final int clusterSize = 3;
    final int replicationFactor = 3;
    final String versionStr = "8.5.0";
    final byte[] versionBytes = versionStr.getBytes(java.nio.charset.StandardCharsets.UTF_8);

    final MutableDirectBuffer buffer = new UnsafeBuffer(new byte[1024]);
    int offset = 0;

    // write header manually with version=7 (not 8)
    final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    headerEncoder.wrap(buffer, offset);
    headerEncoder
        .blockLength(BrokerInfoEncoder.BLOCK_LENGTH)
        .templateId(BrokerInfoEncoder.TEMPLATE_ID)
        .schemaId(BrokerInfoEncoder.SCHEMA_ID)
        .version(7);
    offset += MessageHeaderEncoder.ENCODED_LENGTH;

    // write body using BrokerInfoEncoder.wrap (not wrapAndApplyHeader)
    final BrokerInfoEncoder bodyEncoder = new BrokerInfoEncoder();
    bodyEncoder
        .wrap(buffer, offset)
        .nodeId(nodeId)
        .partitionsCount(partitionsCount)
        .clusterSize(clusterSize)
        .replicationFactor(replicationFactor);

    // empty groups
    bodyEncoder.addressesCount(0);
    bodyEncoder.partitionRolesCount(0);
    bodyEncoder.partitionLeaderTermsCount(0);

    // version var-data (present in v7)
    bodyEncoder.putVersion(versionBytes, 0, versionBytes.length);

    // partitionHealth group (present since v3)
    bodyEncoder.partitionHealthCount(0);

    // zone is NOT written — this is a v7 payload

    final int totalLength = MessageHeaderEncoder.ENCODED_LENGTH + bodyEncoder.encodedLength();

    // when — decode the v7 payload with the current BrokerInfo
    final BrokerInfo decoded = new BrokerInfo();
    assertThatNoException().isThrownBy(() -> decoded.wrap(buffer, 0, totalLength));

    // then — all fields decode correctly, zone is null
    assertThat(decoded.getNodeId()).isEqualTo(nodeId);
    assertThat(decoded.getPartitionsCount()).isEqualTo(partitionsCount);
    assertThat(decoded.getClusterSize()).isEqualTo(clusterSize);
    assertThat(decoded.getReplicationFactor()).isEqualTo(replicationFactor);
    assertThat(decoded.getVersion()).isEqualTo(versionStr);
    assertThat(decoded.getZone()).isNull();
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
