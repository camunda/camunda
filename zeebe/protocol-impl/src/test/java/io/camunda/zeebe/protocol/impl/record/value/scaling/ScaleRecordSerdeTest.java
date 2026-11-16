/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.scaling;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. The V1 class is a frozen copy of the property based implementation the generated codec
 * replaced.
 */
final class ScaleRecordSerdeTest {

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new ScaleRecordV1();
    final var current = new ScaleRecord();
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
    final var v1 = new ScaleRecordV1();
    final var current = new ScaleRecord();

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
    final var v1 = new ScaleRecordV1();
    setAllProperties(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new ScaleRecord();
    setOtherProperties(current);

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getDesiredPartitionCount()).isEqualTo(v1.getDesiredPartitionCount());
    assertThat(current.getRedistributedPartitions()).isEqualTo(v1.getRedistributedPartitions());
    assertThat(current.getRelocatedPartitions()).isEqualTo(v1.getRelocatedPartitions());
    assertThat(current.getMessageCorrelationPartitions())
        .isEqualTo(v1.getMessageCorrelationPartitions());
    assertThat(current.getScalingPosition()).isEqualTo(v1.getScalingPosition());
  }

  @Test
  void shouldRestoreDefaultsWhenReadingBytesWrittenByV1WithDefaults() {
    // given a value with every property at its default
    final var v1 = new ScaleRecordV1();
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new ScaleRecord();
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
    final var current = new ScaleRecord();
    setAllProperties(current);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new ScaleRecordV1();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getDesiredPartitionCount()).isEqualTo(current.getDesiredPartitionCount());
    assertThat(v1.getRedistributedPartitions()).isEqualTo(current.getRedistributedPartitions());
    assertThat(v1.getRelocatedPartitions()).isEqualTo(current.getRelocatedPartitions());
    assertThat(v1.getMessageCorrelationPartitions())
        .isEqualTo(current.getMessageCorrelationPartitions());
    assertThat(v1.getScalingPosition()).isEqualTo(current.getScalingPosition());
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new ScaleRecord();
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
    final var instance = new ScaleRecord();
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
    final var v1 = new ScaleRecordV1();
    final var current = new ScaleRecord();
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

  private static void setAllProperties(final ScaleRecordV1 record) {
    record
        .setDesiredPartitionCount(5)
        .setRedistributedPartitions(List.of(1, 2))
        .setRelocatedPartitions(List.of(3, 4))
        .setMessageCorrelationPartitions(3)
        .setScalingPosition(7L);
  }

  private static void setAllProperties(final ScaleRecord record) {
    record
        .setDesiredPartitionCount(5)
        .setRedistributedPartitions(List.of(1, 2))
        .setRelocatedPartitions(List.of(3, 4))
        .setMessageCorrelationPartitions(3)
        .setScalingPosition(7L);
  }

  private static void setOtherProperties(final ScaleRecord record) {
    record
        .setDesiredPartitionCount(9)
        .setRedistributedPartitions(List.of(8))
        .setRelocatedPartitions(List.of(9))
        .setMessageCorrelationPartitions(8)
        .setScalingPosition(17L);
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
