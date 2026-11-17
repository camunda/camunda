/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.encoding;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link io.camunda.zeebe.protocol.impl.encoding.legacy.AgentInfo} is a frozen copy of the
 * property based implementation the generated codec replaced.
 */
final class AgentInfoSerdeTest {

  private static final String ELEMENT_ID = "an-element-id-that-is-longer-than-thirty-two-bytes";

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new io.camunda.zeebe.protocol.impl.encoding.legacy.AgentInfo();
    final var current = new AgentInfo();
    v1.setElementId(ELEMENT_ID);
    current.setElementId(ELEMENT_ID);

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
    // given only defaults, every property has one
    final var v1 = new io.camunda.zeebe.protocol.impl.encoding.legacy.AgentInfo();
    final var current = new AgentInfo();

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
    final var v1 = new io.camunda.zeebe.protocol.impl.encoding.legacy.AgentInfo();
    v1.setElementId(ELEMENT_ID);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new AgentInfo();
    current.setElementId("dirty");

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getElementId()).isEqualTo(ELEMENT_ID);
  }

  @Test
  void shouldRestoreDefaultsWhenReadingBytesWrittenByV1WithDefaults() {
    // given
    final var defaults = new io.camunda.zeebe.protocol.impl.encoding.legacy.AgentInfo();
    final var bytes = new ExpandableArrayBuffer();
    final int length = defaults.write(bytes, 0);
    final var current = new AgentInfo();
    current.setElementId("dirty");

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getElementId()).isEqualTo(defaults.getElementId()).isEmpty();
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new AgentInfo();
    current.setElementId(ELEMENT_ID);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new io.camunda.zeebe.protocol.impl.encoding.legacy.AgentInfo();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getElementId()).isEqualTo(ELEMENT_ID);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new AgentInfo();
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new AgentInfo();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setElementId("modified");
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  @Test
  void shouldProduceTheSameJsonAsV1() {
    // given
    final var v1 = new io.camunda.zeebe.protocol.impl.encoding.legacy.AgentInfo();
    final var current = new AgentInfo();
    v1.setElementId(ELEMENT_ID);
    current.setElementId(ELEMENT_ID);

    // when
    final var mapper = new ObjectMapper();
    final var v1Json = mapper.valueToTree(v1);
    final var currentJson = mapper.valueToTree(current);

    // then exporters see the same document
    assertThat(currentJson).isEqualTo(v1Json);
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
