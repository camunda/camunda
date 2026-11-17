/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.multiinstance;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link io.camunda.zeebe.engine.state.multiinstance.legacy.PersistedInputCollection} is a
 * frozen copy of the property based implementation the generated codec replaced.
 */
final class PersistedInputCollectionSerdeTest {

  private static final List<String> INPUTS =
      List.of("a-value-that-is-longer-than-thirty-two-bytes-in-total", "b");

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 =
        new io.camunda.zeebe.engine.state.multiinstance.legacy.PersistedInputCollection();
    final var current = new PersistedInputCollection();
    v1.setInputCollection(toBuffers(INPUTS));
    current.setInputCollection(toBuffers(INPUTS));

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
    // given an empty list, the default
    final var v1 =
        new io.camunda.zeebe.engine.state.multiinstance.legacy.PersistedInputCollection();
    final var current = new PersistedInputCollection();

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
    final var v1 =
        new io.camunda.zeebe.engine.state.multiinstance.legacy.PersistedInputCollection();
    v1.setInputCollection(toBuffers(INPUTS));
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new PersistedInputCollection();
    current.setInputCollection(toBuffers(List.of("dirty", "dirty-too")));

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(strings(current.getInputCollection())).isEqualTo(INPUTS);
  }

  @Test
  void shouldResetAListToItsDefaultWhenReadFromV1WithoutItems() {
    // given
    final var v1 =
        new io.camunda.zeebe.engine.state.multiinstance.legacy.PersistedInputCollection();
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new PersistedInputCollection();
    current.setInputCollection(toBuffers(INPUTS));

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getInputCollection()).isEmpty();
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new PersistedInputCollection();
    current.setInputCollection(toBuffers(INPUTS));
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 =
        new io.camunda.zeebe.engine.state.multiinstance.legacy.PersistedInputCollection();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(strings(v1.getInputCollection())).isEqualTo(INPUTS);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new PersistedInputCollection();
    source.setInputCollection(toBuffers(INPUTS));
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // The map has one entry, so its header is 0x81; announce one more entry (0x82).
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new PersistedInputCollection();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setInputCollection(toBuffers(List.of("c")));
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  private static List<DirectBuffer> toBuffers(final List<String> values) {
    return values.stream().map(value -> (DirectBuffer) BufferUtil.wrapString(value)).toList();
  }

  private static List<String> strings(final List<DirectBuffer> buffers) {
    return buffers.stream().map(BufferUtil::bufferAsString).toList();
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
