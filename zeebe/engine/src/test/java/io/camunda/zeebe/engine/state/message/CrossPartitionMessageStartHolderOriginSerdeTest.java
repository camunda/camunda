/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.message;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link CrossPartitionMessageStartHolderOriginV1} is a frozen copy of the property based
 * implementation the generated codec replaced.
 */
final class CrossPartitionMessageStartHolderOriginSerdeTest {

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new CrossPartitionMessageStartHolderOriginV1();
    final var current = new CrossPartitionMessageStartHolderOrigin();
    // a process id longer than 31 bytes, so the msgpack string header changes width
    final var processId = "a-process-id-that-is-longer-than-31-bytes";
    v1.setBpmnProcessId(BufferUtil.wrapString(processId))
        .setCorrelationKey(BufferUtil.wrapString("key-1"))
        .setTenantId("tenant-a")
        .setMessageKey(1L << 40)
        .setStorageOrdinal(200);
    current
        .setBpmnProcessId(BufferUtil.wrapString(processId))
        .setCorrelationKey(BufferUtil.wrapString("key-1"))
        .setTenantId("tenant-a")
        .setMessageKey(1L << 40)
        .setStorageOrdinal(200);

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
    // given only defaults
    final var v1 = new CrossPartitionMessageStartHolderOriginV1();
    final var current = new CrossPartitionMessageStartHolderOrigin();

    // when
    final var v1Bytes = new ExpandableArrayBuffer();
    final int v1Length = v1.write(v1Bytes, 0);
    final var currentBytes = new ExpandableArrayBuffer();
    final int currentLength = current.write(currentBytes, 0);

    // then
    assertThat(hex(currentBytes, currentLength)).isEqualTo(hex(v1Bytes, v1Length));
  }

  @Test
  void shouldReadBytesWrittenByV1() {
    // given
    final var v1 = new CrossPartitionMessageStartHolderOriginV1();
    v1.setBpmnProcessId(BufferUtil.wrapString("process-a"))
        .setCorrelationKey(BufferUtil.wrapString("key-a"))
        .setTenantId("tenant-a")
        .setMessageKey(42L)
        .setStorageOrdinal(3);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new CrossPartitionMessageStartHolderOrigin();
    current
        .setBpmnProcessId(BufferUtil.wrapString("process-b"))
        .setCorrelationKey(BufferUtil.wrapString("key-b"))
        .setTenantId("tenant-b")
        .setMessageKey(-5L)
        .setStorageOrdinal(9);

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(BufferUtil.bufferAsString(current.getBpmnProcessIdBuffer())).isEqualTo("process-a");
    assertThat(BufferUtil.bufferAsString(current.getCorrelationKeyBuffer())).isEqualTo("key-a");
    assertThat(BufferUtil.bufferAsString(current.getTenantIdBuffer())).isEqualTo("tenant-a");
    assertThat(current.getMessageKey()).isEqualTo(42L);
    assertThat(current.getStorageOrdinal()).isEqualTo(3);
  }

  @Test
  void shouldReadDefaultsAfterReusingTheInstance() {
    // given bytes that carry the message key only, the other properties have their defaults
    final var v1 = new CrossPartitionMessageStartHolderOriginV1();
    v1.setMessageKey(42L);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new CrossPartitionMessageStartHolderOrigin();
    current
        .setBpmnProcessId(BufferUtil.wrapString("process-b"))
        .setCorrelationKey(BufferUtil.wrapString("key-b"))
        .setTenantId("tenant-b")
        .setStorageOrdinal(9);

    // when
    current.wrap(bytes, 0, length);

    // then the defaults are restored by reset()
    assertThat(current.getMessageKey()).isEqualTo(42L);
    assertThat(BufferUtil.bufferAsString(current.getBpmnProcessIdBuffer())).isEmpty();
    assertThat(BufferUtil.bufferAsString(current.getCorrelationKeyBuffer())).isEmpty();
    assertThat(BufferUtil.bufferAsString(current.getTenantIdBuffer()))
        .isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    assertThat(current.getStorageOrdinal()).isZero();
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new CrossPartitionMessageStartHolderOrigin();
    current
        .setBpmnProcessId(BufferUtil.wrapString("process-a"))
        .setCorrelationKey(BufferUtil.wrapString("key-a"))
        .setTenantId("tenant-a")
        .setMessageKey(42L)
        .setStorageOrdinal(3);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new CrossPartitionMessageStartHolderOriginV1();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(BufferUtil.bufferAsString(v1.getBpmnProcessIdBuffer())).isEqualTo("process-a");
    assertThat(BufferUtil.bufferAsString(v1.getCorrelationKeyBuffer())).isEqualTo("key-a");
    assertThat(BufferUtil.bufferAsString(v1.getTenantIdBuffer())).isEqualTo("tenant-a");
    assertThat(v1.getMessageKey()).isEqualTo(42L);
    assertThat(v1.getStorageOrdinal()).isEqualTo(3);
  }

  @Test
  void shouldReadAnEmptyMapAsDefaultsLikeV1() {
    // given an empty map, every property keeps its default
    final var bytes = new ExpandableArrayBuffer();
    bytes.putByte(0, (byte) 0x80);

    // when
    final var v1 = new CrossPartitionMessageStartHolderOriginV1();
    final var current = new CrossPartitionMessageStartHolderOrigin();
    v1.wrap(bytes, 0, 1);
    current.wrap(bytes, 0, 1);

    // then
    assertThat(current.getMessageKey()).isEqualTo(v1.getMessageKey());
    assertThat(current.getStorageOrdinal()).isEqualTo(v1.getStorageOrdinal());
    assertThat(BufferUtil.bufferAsString(current.getTenantIdBuffer()))
        .isEqualTo(BufferUtil.bufferAsString(v1.getTenantIdBuffer()));
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new CrossPartitionMessageStartHolderOrigin();
    source.setMessageKey(1L);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // The map has five entries, a header of 0x80 | count, so announce one more.
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new CrossPartitionMessageStartHolderOrigin();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setStorageOrdinal(42);
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
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
