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
import java.util.Properties;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

final class BrokerInfoTest {

  @Test
  void shouldReadBrokerInfoForSpecificPartitionGroup() {
    // given
    final BrokerInfo defaultBroker =
        new BrokerInfo()
            .setBrokerId(1, null)
            .setPartitionsCount(1)
            .setClusterSize(1)
            .setReplicationFactor(1);

    final BrokerInfo tenant1Broker =
        new BrokerInfo()
            .setBrokerId(1, null)
            .setPartitionsCount(1)
            .setClusterSize(1)
            .setReplicationFactor(1)
            .setPartitionGroup("tenant1");
    tenant1Broker.setLeaderForPartition(1, 5L);

    final Properties props = new Properties();
    defaultBroker.writeIntoProperties(props);
    tenant1Broker.writeIntoProperties(props);

    // when / then
    final BrokerInfo readDefault =
        BrokerInfo.fromProperties(props, BrokerInfo.DEFAULT_PARTITION_GROUP);
    assertThat(readDefault).isNotNull();
    assertThat(readDefault.getPartitionGroup()).isEqualTo(BrokerInfo.DEFAULT_PARTITION_GROUP);

    final BrokerInfo readTenant1 = BrokerInfo.fromProperties(props, "tenant1");
    assertThat(readTenant1).isNotNull();
    assertThat(readTenant1.getPartitionGroup()).isEqualTo("tenant1");
    assertThat(readTenant1.getPartitionRoles()).containsKey(1);

    assertThat(BrokerInfo.fromProperties(props, "unknown")).isNull();
  }

  @Test
  void shouldReadAllBrokerInfosFromProperties() {
    // given
    final BrokerInfo defaultBroker =
        new BrokerInfo()
            .setBrokerId(1, null)
            .setPartitionsCount(1)
            .setClusterSize(1)
            .setReplicationFactor(1);
    final BrokerInfo tenant1Broker =
        new BrokerInfo()
            .setBrokerId(1, null)
            .setPartitionsCount(1)
            .setClusterSize(1)
            .setReplicationFactor(1)
            .setPartitionGroup("tenant1");

    final Properties props = new Properties();
    defaultBroker.writeIntoProperties(props);
    tenant1Broker.writeIntoProperties(props);
    props.setProperty("otherProperty", "value");

    // when
    final var all = BrokerInfo.allFromProperties(props);

    // then
    assertThat(all).hasSize(2);
    assertThat(all)
        .extracting(BrokerInfo::getPartitionGroup)
        .containsExactlyInAnyOrder(BrokerInfo.DEFAULT_PARTITION_GROUP, "tenant1");
  }

  @Test
  void shouldReturnEmptyListWhenNoBrokerInfoInProperties() {
    // given
    final Properties props = new Properties();
    props.setProperty("otherProperty", "value");

    // when / then
    assertThat(BrokerInfo.allFromProperties(props)).isEmpty();
  }

  @Test
  void shouldReturnDefaultPropertyNameForDefaultPartitionGroup() {
    assertThat(BrokerInfo.brokerInfoPropertyName(BrokerInfo.DEFAULT_PARTITION_GROUP))
        .isEqualTo("brokerInfo");
  }

  @Test
  void shouldReturnNamespacedPropertyNameForNonDefaultPartitionGroup() {
    assertThat(BrokerInfo.brokerInfoPropertyName("tenant1")).isEqualTo("brokerInfo:tenant1");
  }

  @Test
  void shouldCopyBrokerLevelFieldsWithNewPartitionGroup() {
    // given
    final BrokerInfo original =
        new BrokerInfo()
            .setBrokerId(3, "us-east-1a")
            .setPartitionsCount(3)
            .setClusterSize(3)
            .setReplicationFactor(1)
            .setPartitionGroup("default");
    original.setVersion("8.10.0");
    original.setCommandApiAddress("10.0.0.1:26501");
    original.setLeaderForPartition(1, 5L);

    // when
    final var copy = original.withPartitionGroup("tenant1");

    // then -- broker-level fields are copied
    assertThat(copy.getNodeId()).isEqualTo(3);
    assertThat(copy.getZone()).isEqualTo("us-east-1a");
    assertThat(copy.getPartitionsCount()).isEqualTo(3);
    assertThat(copy.getClusterSize()).isEqualTo(3);
    assertThat(copy.getReplicationFactor()).isEqualTo(1);
    assertThat(copy.getVersion()).isEqualTo("8.10.0");
    assertThat(copy.getCommandApiAddress()).isEqualTo("10.0.0.1:26501");
    assertThat(copy.getPartitionGroup()).isEqualTo("tenant1");
    // partition state is NOT copied
    assertThat(copy.getPartitionRoles()).isEmpty();
    assertThat(copy.getPartitionLeaderTerms()).isEmpty();
    // original is unchanged
    assertThat(original.getPartitionGroup()).isEqualTo("default");
    assertThat(original.getPartitionRoles()).hasSize(1);
  }

  @Test
  void shouldWriteToNamespacedPropertyKeyForNonDefaultGroup() {
    // given
    final BrokerInfo brokerInfo =
        new BrokerInfo()
            .setBrokerId(1, null)
            .setPartitionsCount(1)
            .setClusterSize(1)
            .setReplicationFactor(1)
            .setPartitionGroup("tenant1");

    // when
    final Properties props = new Properties();
    brokerInfo.writeIntoProperties(props);

    // then -- written under the namespaced key, not the legacy key
    assertThat(props.getProperty("brokerInfo:tenant1")).isNotNull();
    assertThat(props.getProperty("brokerInfo")).isNull();
  }

  @Test
  void shouldWriteToLegacyKeyForDefaultGroup() {
    // given
    final BrokerInfo brokerInfo =
        new BrokerInfo()
            .setBrokerId(1, null)
            .setPartitionsCount(1)
            .setClusterSize(1)
            .setReplicationFactor(1);
    // partitionGroup not set → defaults to "default"

    // when
    final Properties props = new Properties();
    brokerInfo.writeIntoProperties(props);

    // then -- written under the legacy key for backward compatibility
    assertThat(props.getProperty("brokerInfo")).isNotNull();
    assertThat(props.getProperty("brokerInfo:default")).isNull();
  }

  @Test
  void shouldEncodeDecodePartitionGroup() {
    // given
    final BrokerInfo brokerInfo =
        new BrokerInfo()
            .setBrokerId(1, null)
            .setPartitionsCount(3)
            .setClusterSize(3)
            .setReplicationFactor(1)
            .setPartitionGroup("tenant1");

    // when
    final var decoded = encodeDecode(brokerInfo);

    // then
    assertThat(decoded.getPartitionGroup()).isEqualTo("tenant1");
  }

  @Test
  void shouldReturnDefaultPartitionGroupWhenNotSet() {
    // given
    final BrokerInfo brokerInfo =
        new BrokerInfo()
            .setBrokerId(1, null)
            .setPartitionsCount(1)
            .setClusterSize(1)
            .setReplicationFactor(1);

    // when
    final var decoded = encodeDecode(brokerInfo);

    // then
    assertThat(decoded.getPartitionGroup()).isEqualTo(BrokerInfo.DEFAULT_PARTITION_GROUP);
  }

  @Test
  void shouldDecodeVersion8PayloadWithoutPartitionGroup() {
    // given -- a v8 payload (has zone but not partitionGroup)
    final int nodeId = 5;
    final int partitionsCount = 3;
    final int clusterSize = 3;
    final int replicationFactor = 1;
    final String versionStr = "8.8.0";
    final byte[] versionBytes = versionStr.getBytes(java.nio.charset.StandardCharsets.UTF_8);

    final MutableDirectBuffer buffer = new UnsafeBuffer(new byte[1024]);
    int offset = 0;

    final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    headerEncoder.wrap(buffer, offset);
    headerEncoder
        .blockLength(BrokerInfoEncoder.BLOCK_LENGTH)
        .templateId(BrokerInfoEncoder.TEMPLATE_ID)
        .schemaId(BrokerInfoEncoder.SCHEMA_ID)
        .version(8);
    offset += MessageHeaderEncoder.ENCODED_LENGTH;

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
    bodyEncoder.zone("eu-west-1a");
    // partitionGroup is NOT written (v8 payload)

    final int totalLength = MessageHeaderEncoder.ENCODED_LENGTH + bodyEncoder.encodedLength();

    // when
    final BrokerInfo decoded = new BrokerInfo();
    assertThatNoException().isThrownBy(() -> decoded.wrap(buffer, 0, totalLength));

    // then -- zone decoded correctly, partitionGroup falls back to default
    assertThat(decoded.getZone()).isEqualTo("eu-west-1a");
    assertThat(decoded.getPartitionGroup()).isEqualTo(BrokerInfo.DEFAULT_PARTITION_GROUP);
  }

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
