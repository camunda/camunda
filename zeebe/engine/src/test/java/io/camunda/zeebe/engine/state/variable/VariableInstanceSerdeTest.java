/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.variable;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableMetadataRecord;
import io.camunda.zeebe.protocol.record.value.ProtectionMode;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.HexFormat;
import java.util.Set;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The stored format must not change, otherwise migration and rollback break. {@link
 * VariableInstanceV1} is a frozen copy of the hand-written implementation the generated codec
 * replaced. The value is a msgpack binary; the bytes below include values that are not valid UTF-8
 * on purpose, so that a string encoding would be caught.
 */
final class VariableInstanceSerdeTest {

  private static final byte[] VALUE = {(byte) 0x81, (byte) 0xa1, (byte) 0xff, 0x00, (byte) 0xc1};

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new VariableInstanceV1();
    final var current = new VariableInstance();
    v1.setKey(42L);
    current.setKey(42L);
    v1.setValue(new UnsafeBuffer(VALUE), 0, VALUE.length);
    current.setValue(new UnsafeBuffer(VALUE), 0, VALUE.length);
    v1.setMetadata(redacted());
    current.setMetadata(redacted());

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
    // given an empty value and no metadata: V1 cannot write the unset key or value, so they are
    // set explicitly to zero and empty, the metadata keeps its default
    final var v1 = new VariableInstanceV1();
    final var current = new VariableInstance();
    v1.setKey(0L);
    current.setKey(0L);
    v1.setValue(new UnsafeBuffer(new byte[0]), 0, 0);
    current.setValue(new UnsafeBuffer(new byte[0]), 0, 0);

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
    // given a V1 value without metadata
    final var v1 = new VariableInstanceV1();
    v1.setKey(42L);
    v1.setValue(new UnsafeBuffer(VALUE), 0, VALUE.length);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new VariableInstance();
    current.setKey(7L);
    current.setValue(new UnsafeBuffer(new byte[] {0x01, 0x02}), 0, 2);
    current.setMetadata(redacted());

    // when
    current.wrap(bytes, 0, length);

    // then the metadata reset restores its empty default
    assertThat(current.getKey()).isEqualTo(42L);
    assertThat(current.getValue()).isEqualTo(new UnsafeBuffer(VALUE));
    assertThat(current.getMetadata().getProtectionModes()).isEmpty();
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new VariableInstance();
    current.setKey(42L);
    current.setValue(new UnsafeBuffer(VALUE), 0, VALUE.length);
    current.setMetadata(redacted());
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new VariableInstanceV1();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getKey()).isEqualTo(42L);
    assertThat(v1.getValue()).isEqualTo(new UnsafeBuffer(VALUE));
    assertThat(v1.getMetadata().getProtectionModes()).containsExactly(ProtectionMode.REDACT);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a stored value that carries a property this version does not know
    final var source = new VariableInstance();
    source.setKey(42L);
    source.setValue(new UnsafeBuffer(VALUE), 0, VALUE.length);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(wrapString("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // three declared properties: a fixmap header (0x80 | count), announce one more entry
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new VariableInstance();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setKey(43L);
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    final var reread = new VariableInstance();
    reread.wrap(rewritten, 0, rewrittenLength);
    assertThat(reread.getKey()).isEqualTo(43L);
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  @Test
  void shouldNotReferenceTheBufferItWasReadFrom() {
    // given
    final var instance = new VariableInstance();
    instance.setKey(42L);
    instance.setValue(new UnsafeBuffer(VALUE), 0, VALUE.length);
    instance.setMetadata(redacted());
    final var buffer = new ExpandableArrayBuffer();
    final int length = instance.write(buffer, 0);
    final var read = new VariableInstance();
    read.wrap(buffer, 0, length);

    // when the buffer is reused
    buffer.setMemory(0, buffer.capacity(), (byte) 0);

    // then
    assertThat(read.getValue()).isEqualTo(new UnsafeBuffer(VALUE));
    assertThat(read.getMetadata().getProtectionModes()).containsExactly(ProtectionMode.REDACT);
  }

  private static VariableMetadataRecord redacted() {
    return new VariableMetadataRecord().setProtectionModes(Set.of(ProtectionMode.REDACT));
  }

  private static DirectBuffer wrapString(final String s) {
    return BufferUtil.wrapString(s);
  }

  private static String hex(final ExpandableArrayBuffer buffer, final int length) {
    final var bytes = new byte[length];
    buffer.getBytes(0, bytes);
    return HexFormat.of().formatHex(bytes);
  }
}
