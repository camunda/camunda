/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.msgpack.MsgpackPropertyException;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link MessageRecordV1} is a frozen copy of the property based implementation the
 * generated codec replaced.
 */
final class MessageRecordSerdeTest {

  // the msgpack map {"a": 1}
  private static final DirectBuffer VARIABLES =
      new UnsafeBuffer(new byte[] {(byte) 0x81, (byte) 'a', 0x01});
  private static final String TENANT = "a tenant id that is longer than forty bytes of utf8";

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new MessageRecordV1();
    final var current = new MessageRecord();
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
    final var v1 = new MessageRecordV1();
    final var current = new MessageRecord();
    v1.setName("name");
    v1.setCorrelationKey("key");
    v1.setTimeToLive(1L);
    current.setName("name");
    current.setCorrelationKey("key");
    current.setTimeToLive(1L);

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
    final var v1 = new MessageRecordV1();
    final var current = new MessageRecord();

    // when / then
    assertThatThrownBy(() -> v1.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(MsgpackPropertyException.class);
    assertThatThrownBy(() -> current.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(MsgpackPropertyException.class);
  }

  @Test
  void shouldReadBytesWrittenByV1() {
    // given
    final var v1 = new MessageRecordV1();
    setAllProperties(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new MessageRecord();
    current.setName("dirty");
    current.setCorrelationKey("dirty");
    current.setTimeToLive(1L);
    current.setDeadline(2L);
    current.setVariables(new UnsafeBuffer(new byte[] {(byte) 0x80}));
    current.setMessageId("dirty");
    current.setTenantId("dirty");
    current.setBusinessId("dirty");

    // when
    current.wrap(bytes, 0, length);

    // then every value was restored and no dirtied property survived
    assertThat(current.getName()).isEqualTo("name");
    assertThat(current.getCorrelationKey()).isEqualTo("key");
    assertThat(current.getTimeToLive()).isEqualTo(70000L);
    assertThat(current.getDeadline()).isEqualTo(300L);
    assertThat(current.getVariablesBuffer()).isEqualTo(VARIABLES);
    assertThat(current.getMessageId()).isEqualTo("message id");
    assertThat(current.getTenantId()).isEqualTo(TENANT);
    assertThat(current.getBusinessId()).isEqualTo("business");
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = current.write(rewritten, 0);
    assertThat(hex(rewritten, rewrittenLength)).isEqualTo(hex(bytes, length));
  }

  @Test
  void shouldRestoreDefaultsWhenReadingBytesWrittenByV1WithDefaults() {
    // given a value with every property at its default
    final var v1 = new MessageRecordV1();
    v1.setName("name");
    v1.setCorrelationKey("key");
    v1.setTimeToLive(1L);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new MessageRecord();
    setAllProperties(current);

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
    final var current = new MessageRecord();
    setAllProperties(current);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new MessageRecordV1();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getName()).isEqualTo("name");
    assertThat(v1.getCorrelationKey()).isEqualTo("key");
    assertThat(v1.getTimeToLive()).isEqualTo(70000L);
    assertThat(v1.getDeadline()).isEqualTo(300L);
    assertThat(v1.getVariablesBuffer()).isEqualTo(VARIABLES);
    assertThat(v1.getMessageId()).isEqualTo("message id");
    assertThat(v1.getTenantId()).isEqualTo(TENANT);
    assertThat(v1.getBusinessId()).isEqualTo("business");
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = v1.write(rewritten, 0);
    assertThat(hex(rewritten, rewrittenLength)).isEqualTo(hex(bytes, length));
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new MessageRecord();
    setAllProperties(source);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // the map header is one byte, announce one more entry
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new MessageRecord();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setDeadline(9L);
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  @Test
  void shouldProduceTheSameJsonAsV1() throws Exception {
    // given
    final var v1 = new MessageRecordV1();
    final var current = new MessageRecord();
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

  private static void setAllProperties(final MessageRecordV1 record) {
    record.setName("name");
    record.setCorrelationKey("key");
    record.setTimeToLive(70000L);
    record.setDeadline(300L);
    record.setVariables(VARIABLES);
    record.setMessageId("message id");
    record.setTenantId(TENANT);
    record.setBusinessId("business");
  }

  private static void setAllProperties(final MessageRecord record) {
    record.setName("name");
    record.setCorrelationKey("key");
    record.setTimeToLive(70000L);
    record.setDeadline(300L);
    record.setVariables(VARIABLES);
    record.setMessageId("message id");
    record.setTenantId(TENANT);
    record.setBusinessId("business");
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
