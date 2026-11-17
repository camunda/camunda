/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.stream.job;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise mixed version clusters break while a job is
 * pushed from a broker to a gateway. {@link
 * io.camunda.zeebe.protocol.impl.stream.job.legacy.ActivatedJobImpl} is a frozen copy of the
 * property based implementation the generated codec replaced.
 */
final class ActivatedJobImplSerdeTest {

  private static final String TYPE = "a-job-type-that-is-longer-than-thirty-two-bytes";
  private static final DirectBuffer VARIABLES =
      new UnsafeBuffer(MsgPackConverter.convertToMsgPack(Map.of("variable", 1)));

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new io.camunda.zeebe.protocol.impl.stream.job.legacy.ActivatedJobImpl();
    final var current = new ActivatedJobImpl();
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
    // given everything stays default
    final var v1 = new io.camunda.zeebe.protocol.impl.stream.job.legacy.ActivatedJobImpl();
    final var current = new ActivatedJobImpl();

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
    final var v1 = new io.camunda.zeebe.protocol.impl.stream.job.legacy.ActivatedJobImpl();
    setAllProperties(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new ActivatedJobImpl();
    current.setJobKey(7L).setRecord(new JobRecord().setType("dirty").setRetries(9));

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.jobKey()).isEqualTo(42L);
    assertThat(current.jobRecord().getType()).isEqualTo(TYPE);
    assertThat(current.jobRecord().getRetries()).isEqualTo(3);
    assertThat(current.jobRecord().getVariables()).containsExactly(Map.entry("variable", 1));
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new ActivatedJobImpl();
    setAllProperties(current);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new io.camunda.zeebe.protocol.impl.stream.job.legacy.ActivatedJobImpl();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.jobKey()).isEqualTo(42L);
    assertThat(v1.jobRecord().getType()).isEqualTo(TYPE);
    assertThat(v1.jobRecord().getRetries()).isEqualTo(3);
    assertThat(v1.jobRecord().getVariables()).containsExactly(Map.entry("variable", 1));
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new ActivatedJobImpl();
    setAllProperties(source);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // the map has two entries, plus one unknown: 0x82 becomes 0x83
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new ActivatedJobImpl();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setJobKey(9L);
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  private static void setAllProperties(
      final io.camunda.zeebe.protocol.impl.stream.job.legacy.ActivatedJobImpl record) {
    record
        .setJobKey(42L)
        .setRecord(
            new io.camunda.zeebe.protocol.impl.record.value.job.legacy.JobRecord()
                .setType(TYPE)
                .setRetries(3)
                .setVariables(VARIABLES));
  }

  private static void setAllProperties(final ActivatedJobImpl record) {
    record
        .setJobKey(42L)
        .setRecord(new JobRecord().setType(TYPE).setRetries(3).setVariables(VARIABLES));
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
