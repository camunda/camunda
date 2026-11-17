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
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
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
 * break. {@link io.camunda.zeebe.engine.state.message.legacy.MessageStartProcessInstanceAsk} is a
 * frozen copy of the property based implementation the generated codec replaced.
 */
final class MessageStartProcessInstanceAskSerdeTest {

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 =
        new io.camunda.zeebe.engine.state.message.legacy.MessageStartProcessInstanceAsk();
    final var current = new MessageStartProcessInstanceAsk();
    setAll(v1);
    setAll(current);

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
    // given only defaults, the variables default to the empty document like V1
    final var v1 =
        new io.camunda.zeebe.engine.state.message.legacy.MessageStartProcessInstanceAsk();
    final var current = new MessageStartProcessInstanceAsk();

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
        new io.camunda.zeebe.engine.state.message.legacy.MessageStartProcessInstanceAsk();
    setAll(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new MessageStartProcessInstanceAsk();
    setAll(current);
    current.setMessageKey(-5L).setMessageName("other").setRejectionCount(9L);

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getMessageKey()).isEqualTo(1L << 40);
    assertThat(BufferUtil.bufferAsString(current.getMessageNameBuffer())).isEqualTo("message-name");
    assertThat(current.getMessageDeadline()).isEqualTo(7L);
    assertThat(current.getRejectionCount()).isEqualTo(300L);
    assertThat(hex(current.getVariablesBuffer())).isEqualTo(hex(variables()));
  }

  @Test
  void shouldReadDefaultsAfterReusingTheInstance() {
    // given bytes that carry the variables only, everything else has its default
    final var v1 =
        new io.camunda.zeebe.engine.state.message.legacy.MessageStartProcessInstanceAsk();
    v1.setVariables(variables());
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new MessageStartProcessInstanceAsk();
    setAll(current);

    // when
    current.wrap(bytes, 0, length);

    // then the defaults are restored by reset()
    assertThat(current.getMessageKey()).isEqualTo(-1L);
    assertThat(current.getMessageNameBuffer().capacity()).isZero();
    assertThat(current.getProcessDefinitionKey()).isEqualTo(-1L);
    assertThat(current.getMessageStartEventSubscriptionKey()).isEqualTo(-1L);
    assertThat(BufferUtil.bufferAsString(current.getTenantIdBuffer()))
        .isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    assertThat(current.getMessageDeadline()).isEqualTo(-1L);
    assertThat(current.getMessageTtl()).isZero();
    assertThat(current.getRejectionCount()).isZero();
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new MessageStartProcessInstanceAsk();
    setAll(current);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 =
        new io.camunda.zeebe.engine.state.message.legacy.MessageStartProcessInstanceAsk();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getMessageKey()).isEqualTo(1L << 40);
    assertThat(BufferUtil.bufferAsString(v1.getMessageNameBuffer())).isEqualTo("message-name");
    assertThat(v1.getMessageDeadline()).isEqualTo(7L);
    assertThat(v1.getRejectionCount()).isEqualTo(300L);
    assertThat(hex(v1.getVariablesBuffer())).isEqualTo(hex(variables()));
  }

  @Test
  void shouldReadAnEmptyMapAsDefaultsLikeV1() {
    // given an empty map
    final var bytes = new ExpandableArrayBuffer();
    bytes.putByte(0, (byte) 0x80);

    // when
    final var v1 =
        new io.camunda.zeebe.engine.state.message.legacy.MessageStartProcessInstanceAsk();
    final var current = new MessageStartProcessInstanceAsk();
    v1.wrap(bytes, 0, 1);
    current.wrap(bytes, 0, 1);

    // then every property has its default, like in V1
    assertThat(current.getMessageKey()).isEqualTo(v1.getMessageKey());
    assertThat(current.getRejectionCount()).isEqualTo(v1.getRejectionCount());
    assertThat(hex(current.getVariablesBuffer())).isEqualTo(hex(v1.getVariablesBuffer()));
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new MessageStartProcessInstanceAsk();
    setAll(source);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // The map has 13 entries, a header of 0x80 | count (up to 15 entries), so announce one more.
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new MessageStartProcessInstanceAsk();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setRejectionCount(42L);
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  private static void setAll(
      final io.camunda.zeebe.engine.state.message.legacy.MessageStartProcessInstanceAsk ask) {
    ask.setMessageKey(1L << 40)
        .setMessageName("message-name")
        .setCorrelationKey("correlation-key")
        .setBusinessId("business-id")
        .setProcessDefinitionKey(5L)
        .setBpmnProcessId("process-id")
        .setStartEventId("start-event")
        .setMessageStartEventSubscriptionKey(6L)
        .setVariables(variables())
        .setTenantId("tenant-a")
        .setMessageDeadline(7L)
        .setMessageTtl(8L)
        .setRejectionCount(300L);
  }

  private static void setAll(final MessageStartProcessInstanceAsk ask) {
    ask.setMessageKey(1L << 40)
        .setMessageName("message-name")
        .setCorrelationKey("correlation-key")
        .setBusinessId("business-id")
        .setProcessDefinitionKey(5L)
        .setBpmnProcessId("process-id")
        .setStartEventId("start-event")
        .setMessageStartEventSubscriptionKey(6L)
        .setVariables(variables())
        .setTenantId("tenant-a")
        .setMessageDeadline(7L)
        .setMessageTtl(8L)
        .setRejectionCount(300L);
  }

  private static DirectBuffer variables() {
    return BufferUtil.wrapArray(MsgPackConverter.convertToMsgPack("{\"key\":\"value\"}"));
  }

  private static DirectBuffer utf8(final String s) {
    return new UnsafeBuffer(s.getBytes(StandardCharsets.UTF_8));
  }

  private static String hex(final DirectBuffer buffer) {
    final var bytes = new byte[buffer.capacity()];
    buffer.getBytes(0, bytes);
    return HexFormat.of().formatHex(bytes);
  }

  private static String hex(final ExpandableArrayBuffer buffer, final int length) {
    final var bytes = new byte[length];
    buffer.getBytes(0, bytes);
    return HexFormat.of().formatHex(bytes);
  }
}
