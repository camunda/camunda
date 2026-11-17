/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.routing;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.immutable.RoutingState.MessageCorrelation;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link io.camunda.zeebe.engine.state.routing.legacy.PersistedRoutingInfo} is a frozen copy
 * of the property based implementation the generated codec replaced.
 */
final class PersistedRoutingInfoSerdeTest {

  // 1 needs one byte, 200 two and 70000 four bytes in msgpack, so every width is covered
  private static final SortedSet<Integer> PARTITIONS = new TreeSet<>(List.of(1, 200, 70_000));

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new io.camunda.zeebe.engine.state.routing.legacy.PersistedRoutingInfo();
    final var current = new PersistedRoutingInfo();
    v1.setPartitions(PARTITIONS);
    current.setPartitions(PARTITIONS);
    v1.setMessageCorrelation(new MessageCorrelation.HashMod(5));
    current.setMessageCorrelation(new MessageCorrelation.HashMod(5));

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
    final var v1 = new io.camunda.zeebe.engine.state.routing.legacy.PersistedRoutingInfo();
    final var current = new PersistedRoutingInfo();

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
    final var v1 = new io.camunda.zeebe.engine.state.routing.legacy.PersistedRoutingInfo();
    v1.setPartitions(PARTITIONS);
    v1.setMessageCorrelation(new MessageCorrelation.HashMod(5));
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new PersistedRoutingInfo();
    current.setPartitions(new TreeSet<>(List.of(9)));
    current.setMessageCorrelation(new MessageCorrelation.HashMod(7));

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getPartitions()).isEqualTo(PARTITIONS);
    assertThat(current.getMessageCorrelation()).isEqualTo(new MessageCorrelation.HashMod(5));
  }

  @Test
  void shouldResetToDefaultsWhenReadFromV1WithDefaults() {
    // given
    final var v1 = new io.camunda.zeebe.engine.state.routing.legacy.PersistedRoutingInfo();
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new PersistedRoutingInfo();
    current.setPartitions(new TreeSet<>(List.of(9)));
    current.setMessageCorrelation(new MessageCorrelation.HashMod(7));

    // when
    current.wrap(bytes, 0, length);

    // then the defaults are back: the same bytes as a fresh V1 (its hash mod count is -1, which
    // MessageCorrelation rejects, so the defaults are compared on the wire, not through getters)
    assertThat(current.getPartitions()).isEmpty();
    final var defaults = new ExpandableArrayBuffer();
    final int defaultsLength =
        new io.camunda.zeebe.engine.state.routing.legacy.PersistedRoutingInfo().write(defaults, 0);
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = current.write(rewritten, 0);
    assertThat(hex(rewritten, rewrittenLength)).isEqualTo(hex(defaults, defaultsLength));
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new PersistedRoutingInfo();
    current.setPartitions(PARTITIONS);
    current.setMessageCorrelation(new MessageCorrelation.HashMod(5));
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new io.camunda.zeebe.engine.state.routing.legacy.PersistedRoutingInfo();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getPartitions()).isEqualTo(PARTITIONS);
    assertThat(v1.getMessageCorrelation()).isEqualTo(new MessageCorrelation.HashMod(5));
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new PersistedRoutingInfo();
    source.setPartitions(PARTITIONS);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // The map has three entries, so its header is 0x83; announce one more entry (0x84).
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new PersistedRoutingInfo();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setMessageCorrelation(new MessageCorrelation.HashMod(2));
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
