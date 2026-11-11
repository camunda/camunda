/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import java.util.HexFormat;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The stored format must not change, otherwise migration and rollback break. {@link DigestV1} is a
 * frozen copy of the hand-written implementation the generated codec replaced. The digest is a
 * msgpack binary, and the values below include bytes that are not valid UTF-8 on purpose, so that a
 * string encoding would be caught.
 */
final class DigestSerdeTest {

  private static final byte[] DIGEST = {0x00, 0x7f, (byte) 0x80, (byte) 0xc1, (byte) 0xff, 0x2a};

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new DigestV1();
    final var current = new Digest();
    v1.set(new UnsafeBuffer(DIGEST));
    current.set(new UnsafeBuffer(DIGEST));

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
    // given an empty digest: V1 cannot write an unset BinaryProperty, so the empty value is
    // explicit
    final var v1 = new DigestV1();
    final var current = new Digest();
    v1.set(new UnsafeBuffer(new byte[0]));
    current.set(new UnsafeBuffer(new byte[0]));

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
    final var v1 = new DigestV1();
    v1.set(new UnsafeBuffer(DIGEST));
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new Digest();
    current.set(new UnsafeBuffer(new byte[] {0x11, 0x22}));

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.get()).isEqualTo(new UnsafeBuffer(DIGEST));
  }

  @Test
  void shouldReadAnEmptyDigestWrittenByV1IntoADirtyTarget() {
    // given a V1 value with an empty digest
    final var v1 = new DigestV1();
    v1.set(new UnsafeBuffer(new byte[0]));
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new Digest();
    current.set(new UnsafeBuffer(DIGEST));

    // when
    current.wrap(bytes, 0, length);

    // then the reset and the read leave the empty value
    assertThat(current.get().capacity()).isZero();
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new Digest();
    current.set(new UnsafeBuffer(DIGEST));
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new DigestV1();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.get()).isEqualTo(new UnsafeBuffer(DIGEST));
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a stored value that carries a property this version does not know
    final var source = new Digest();
    source.set(new UnsafeBuffer(DIGEST));
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(wrapString("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // one declared property: a fixmap header (0x80 | count), announce one more entry
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new Digest();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.set(new UnsafeBuffer(new byte[] {0x33}));
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  @Test
  void shouldNotReferenceTheBufferItWasReadFrom() {
    // given
    final var instance = new Digest();
    instance.set(new UnsafeBuffer(DIGEST));
    final var buffer = new ExpandableArrayBuffer();
    final int length = instance.write(buffer, 0);
    final var read = new Digest();
    read.wrap(buffer, 0, length);

    // when the buffer is reused
    buffer.setMemory(0, buffer.capacity(), (byte) 0);

    // then
    assertThat(read.get()).isEqualTo(new UnsafeBuffer(DIGEST));
  }

  private static DirectBuffer wrapString(final String s) {
    return new UnsafeBuffer(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  private static String hex(final ExpandableArrayBuffer buffer, final int length) {
    final var bytes = new byte[length];
    buffer.getBytes(0, bytes);
    return HexFormat.of().formatHex(bytes);
  }
}
