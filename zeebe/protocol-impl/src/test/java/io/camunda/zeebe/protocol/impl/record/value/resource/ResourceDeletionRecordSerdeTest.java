/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.msgpack.MsgpackPropertyException;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import io.camunda.zeebe.protocol.record.value.ResourceType;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. The V1 class is a frozen copy of the property based implementation the generated codec
 * replaced.
 */
final class ResourceDeletionRecordSerdeTest {

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.resource.legacy.ResourceDeletionRecord();
    final var current = new ResourceDeletionRecord();
    setAllProperties(v1);
    setAllProperties(current);

    // when
    final var v1Bytes = new ExpandableArrayBuffer();
    final int v1Length = v1.write(v1Bytes, 0);
    final var currentBytes = new ExpandableArrayBuffer();
    final int currentLength = current.write(currentBytes, 0);

    // then
    assertThat(currentLength).isEqualTo(current.getLength()).isEqualTo(v1.getLength());
    assertThat(hex(currentBytes, currentLength)).isEqualTo(hex(v1Bytes, v1Length));
  }

  @Test
  void shouldWriteTheSameBytesAsV1WithDefaults() {
    // given only what cannot be written unset, everything else stays default
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.resource.legacy.ResourceDeletionRecord();
    final var current = new ResourceDeletionRecord();
    setRequiredProperties(v1);
    setRequiredProperties(current);

    // when
    final var v1Bytes = new ExpandableArrayBuffer();
    final int v1Length = v1.write(v1Bytes, 0);
    final var currentBytes = new ExpandableArrayBuffer();
    final int currentLength = current.write(currentBytes, 0);

    // then
    assertThat(hex(currentBytes, currentLength)).isEqualTo(hex(v1Bytes, v1Length));
  }

  @Test
  void shouldFailToWriteWhileUnsetLikeV1() {
    // given
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.resource.legacy.ResourceDeletionRecord();
    final var current = new ResourceDeletionRecord();

    // when / then
    assertThatThrownBy(() -> v1.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(MsgpackPropertyException.class);
    assertThatThrownBy(() -> current.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(MsgpackPropertyException.class);
  }

  @Test
  void shouldFailToReadWhilePropertiesAreMissingLikeV1() {
    // given an empty map
    final var empty = new UnsafeBuffer(new byte[] {(byte) 0x80});
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.resource.legacy.ResourceDeletionRecord();
    final var current = new ResourceDeletionRecord();

    // when / then
    assertThatThrownBy(() -> v1.wrap(empty, 0, empty.capacity()))
        .isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> current.wrap(empty, 0, empty.capacity()))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void shouldReadBytesWrittenByV1() {
    // given
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.resource.legacy.ResourceDeletionRecord();
    setAllProperties(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new ResourceDeletionRecord();
    setOtherProperties(current);

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getResourceKey()).isEqualTo(v1.getResourceKey());
    assertThat(current.getTenantId()).isEqualTo(v1.getTenantId());
    assertThat(current.isDeleteHistory()).isEqualTo(v1.isDeleteHistory());
    assertThat(current.getBatchOperationKey()).isEqualTo(v1.getBatchOperationKey());
    assertThat(current.getStorageOrdinal()).isEqualTo(v1.getStorageOrdinal());
    assertThat(current.getBatchOperationType()).isEqualTo(v1.getBatchOperationType());
    assertThat(current.getResourceType()).isEqualTo(v1.getResourceType());
    assertThat(current.getResourceId()).isEqualTo(v1.getResourceId());
  }

  @Test
  void shouldRestoreDefaultsWhenReadingBytesWrittenByV1WithDefaults() {
    // given a value with every property at its default
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.resource.legacy.ResourceDeletionRecord();
    setRequiredProperties(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new ResourceDeletionRecord();
    setOtherProperties(current);

    // when
    current.wrap(bytes, 0, length);

    // then re-encoding gives the bytes of the defaults, so no dirtied property survived
    final var currentBytes = new ExpandableArrayBuffer();
    final int currentLength = current.write(currentBytes, 0);
    assertThat(hex(currentBytes, currentLength)).isEqualTo(hex(bytes, length));
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new ResourceDeletionRecord();
    setAllProperties(current);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.resource.legacy.ResourceDeletionRecord();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getResourceKey()).isEqualTo(current.getResourceKey());
    assertThat(v1.getTenantId()).isEqualTo(current.getTenantId());
    assertThat(v1.isDeleteHistory()).isEqualTo(current.isDeleteHistory());
    assertThat(v1.getBatchOperationKey()).isEqualTo(current.getBatchOperationKey());
    assertThat(v1.getStorageOrdinal()).isEqualTo(current.getStorageOrdinal());
    assertThat(v1.getBatchOperationType()).isEqualTo(current.getBatchOperationType());
    assertThat(v1.getResourceType()).isEqualTo(current.getResourceType());
    assertThat(v1.getResourceId()).isEqualTo(current.getResourceId());
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new ResourceDeletionRecord();
    setAllProperties(source);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // the map has fewer than 16 entries, so the header is one byte (0x80 | count): add one
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new ResourceDeletionRecord();
    instance.wrap(stored, 0, storedLength + futureLength);
    setOtherProperties(instance);
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  @Test
  void shouldProduceTheSameJsonAsV1() throws Exception {
    // given
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.resource.legacy.ResourceDeletionRecord();
    final var current = new ResourceDeletionRecord();
    setAllProperties(v1);
    setAllProperties(current);

    // when
    final var v1Json = v1.toJson();
    final var currentJson = current.toJson();

    // then exporters see the same document; compare the trees, Jackson does not guarantee the order
    // of the properties and it differs between the old and the new class
    final var mapper = new ObjectMapper();
    assertThat(mapper.readTree(currentJson)).isEqualTo(mapper.readTree(v1Json));
  }

  private static void setAllProperties(
      final io.camunda.zeebe.protocol.impl.record.value.resource.legacy.ResourceDeletionRecord
          record) {
    record
        .setResourceKey(1L)
        .setTenantId("tenant")
        .setDeleteHistory(true)
        .setBatchOperationKey(2L)
        .setStorageOrdinal(3)
        .setBatchOperationType(BatchOperationType.MIGRATE_PROCESS_INSTANCE)
        .setResourceType(ResourceType.PROCESS_DEFINITION)
        .setResourceId("resource");
  }

  private static void setAllProperties(final ResourceDeletionRecord record) {
    record
        .setResourceKey(1L)
        .setTenantId("tenant")
        .setDeleteHistory(true)
        .setBatchOperationKey(2L)
        .setStorageOrdinal(3)
        .setBatchOperationType(BatchOperationType.MIGRATE_PROCESS_INSTANCE)
        .setResourceType(ResourceType.PROCESS_DEFINITION)
        .setResourceId("resource");
  }

  private static void setOtherProperties(final ResourceDeletionRecord record) {
    record
        .setResourceKey(11L)
        .setTenantId("other")
        .setDeleteHistory(false)
        .setBatchOperationKey(12L)
        .setStorageOrdinal(13)
        .setBatchOperationType(BatchOperationType.DELETE_PROCESS_INSTANCE)
        .setResourceType(ResourceType.FORM)
        .setResourceId("other");
  }

  private static void setRequiredProperties(
      final io.camunda.zeebe.protocol.impl.record.value.resource.legacy.ResourceDeletionRecord
          record) {
    record.setResourceKey(1L);
  }

  private static void setRequiredProperties(final ResourceDeletionRecord record) {
    record.setResourceKey(1L);
  }

  private static DirectBuffer utf8(final String s) {
    return new UnsafeBuffer(s.getBytes(StandardCharsets.UTF_8));
  }

  private static String hex(final ExpandableArrayBuffer buffer, final int length) {
    final var bytes = new byte[length];
    buffer.getBytes(0, bytes);
    return HexFormat.of().formatHex(bytes);
  }
}
