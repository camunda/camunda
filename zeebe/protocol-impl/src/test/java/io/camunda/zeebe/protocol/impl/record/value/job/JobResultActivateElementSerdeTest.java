/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.msgpack.MsgpackPropertyException;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link io.camunda.zeebe.protocol.impl.record.value.job.legacy.JobResultActivateElement} is
 * a frozen copy of the property based implementation the generated codec replaced.
 */
final class JobResultActivateElementSerdeTest {

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.job.legacy.JobResultActivateElement();
    final var current = new JobResultActivateElement();
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
        new io.camunda.zeebe.protocol.impl.record.value.job.legacy.JobResultActivateElement();
    final var current = new JobResultActivateElement();
    v1.setElementId("task");
    current.setElementId("task");

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
        new io.camunda.zeebe.protocol.impl.record.value.job.legacy.JobResultActivateElement();
    final var current = new JobResultActivateElement();

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
        new io.camunda.zeebe.protocol.impl.record.value.job.legacy.JobResultActivateElement();
    final var current = new JobResultActivateElement();

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
        new io.camunda.zeebe.protocol.impl.record.value.job.legacy.JobResultActivateElement();
    setAllProperties(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new JobResultActivateElement();
    current.setElementId("dirty").setVariables(otherVariables());

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getElementId()).isEqualTo(v1.getElementId());
    assertThat(current.getVariables()).containsOnlyKeys("a", "b").isEqualTo(v1.getVariables());
  }

  @Test
  void shouldRestoreDefaultVariablesWhenReadingBytesWrittenByV1WithDefaults() {
    // given
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.job.legacy.JobResultActivateElement();
    v1.setElementId("task");
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new JobResultActivateElement();
    current.setElementId("dirty").setVariables(otherVariables());

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getElementId()).isEqualTo("task");
    assertThat(current.getVariables()).isEqualTo(v1.getVariables()).isEmpty();
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new JobResultActivateElement();
    setAllProperties(current);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.job.legacy.JobResultActivateElement();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getElementId()).isEqualTo(current.getElementId());
    assertThat(v1.getVariables()).isEqualTo(current.getVariables());
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new JobResultActivateElement();
    source.setElementId("task");
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new JobResultActivateElement();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setElementId("other");
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
        new io.camunda.zeebe.protocol.impl.record.value.job.legacy.JobResultActivateElement();
    final var current = new JobResultActivateElement();
    setAllProperties(v1);
    setAllProperties(current);

    // when
    final var mapper = new ObjectMapper();
    final var v1Json = mapper.readTree(mapper.writeValueAsString(v1));
    final var currentJson = mapper.readTree(mapper.writeValueAsString(current));

    // then the parent record serializes the value with Jackson, so the documents must be equal
    assertThat(currentJson).isEqualTo(v1Json);
  }

  @Test
  void shouldCopyAllPropertiesWithCopyFrom() {
    // given
    final var source = new JobResultActivateElement();
    setAllProperties(source);

    // when
    final var copy = new JobResultActivateElement();
    copy.copyFrom(source);

    // then
    assertThat(copy.getElementId()).isEqualTo(source.getElementId());
    assertThat(copy.getVariables()).contains(entry("a", 70_000)).isEqualTo(source.getVariables());
  }

  private static void setAllProperties(
      final io.camunda.zeebe.protocol.impl.record.value.job.legacy.JobResultActivateElement x) {
    x.setElementId("an-element-id-that-is-longer-than-thirty-two-bytes").setVariables(variables());
  }

  private static void setAllProperties(final JobResultActivateElement x) {
    x.setElementId("an-element-id-that-is-longer-than-thirty-two-bytes").setVariables(variables());
  }

  private static DirectBuffer variables() {
    return new UnsafeBuffer(
        MsgPackConverter.convertToMsgPack(
            "{\"a\":70000,\"b\":\"a-string-above-thirty-one-bytes-long\"}"));
  }

  private static DirectBuffer otherVariables() {
    return new UnsafeBuffer(MsgPackConverter.convertToMsgPack("{\"other\":1}"));
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
