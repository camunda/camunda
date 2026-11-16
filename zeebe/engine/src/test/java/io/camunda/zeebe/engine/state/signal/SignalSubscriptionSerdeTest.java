/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.signal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.msgpack.MsgpackPropertyException;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.impl.record.value.signal.SignalSubscriptionRecord;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link SignalSubscriptionV1} is a frozen copy of the property based implementation the
 * generated codec replaced.
 */
final class SignalSubscriptionSerdeTest {

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new SignalSubscriptionV1();
    final var current = new SignalSubscription();
    final var record = record("signal-name-that-is-longer-than-thirty-two-bytes");
    v1.setRecord(record);
    v1.setKey(4242L);
    current.setRecord(record);
    current.setKey(4242L);

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
    // given the key, which cannot be written unset, and the default record
    final var v1 = new SignalSubscriptionV1();
    final var current = new SignalSubscription();
    v1.setKey(0L);
    current.setKey(0L);

    // when
    final var v1Bytes = new ExpandableArrayBuffer();
    final int v1Length = v1.write(v1Bytes, 0);
    final var currentBytes = new ExpandableArrayBuffer();
    final int currentLength = current.write(currentBytes, 0);

    // then
    assertThat(hex(currentBytes, currentLength)).isEqualTo(hex(v1Bytes, v1Length));
  }

  @Test
  void shouldFailToWriteWhileKeyIsUnsetLikeV1() {
    // given
    final var v1 = new SignalSubscriptionV1();
    final var current = new SignalSubscription();

    // when / then
    assertThatThrownBy(() -> v1.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(MsgpackPropertyException.class);
    assertThatThrownBy(() -> current.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(MsgpackPropertyException.class);
  }

  @Test
  void shouldReadBytesWrittenByV1() {
    // given
    final var v1 = new SignalSubscriptionV1();
    v1.setRecord(record("signal-a"));
    v1.setKey(42L);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new SignalSubscription();
    current.setRecord(record("dirty-signal"));
    current.setKey(7L);

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getRecord().getSignalName()).isEqualTo("signal-a");
    assertThat(current.getRecord().getProcessInstanceKey()).isEqualTo(12L);
    assertThat(current.getKey()).isEqualTo(42L);
  }

  @Test
  void shouldResetTheRecordToItsDefaultWhenReadFromV1WithoutIt() {
    // given
    final var v1 = new SignalSubscriptionV1();
    v1.setKey(0L);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new SignalSubscription();
    current.setRecord(record("dirty-signal"));
    current.setKey(7L);

    // when
    current.wrap(bytes, 0, length);

    // then the defaults are back: the same bytes as a fresh V1 with the same key
    final var defaults = new ExpandableArrayBuffer();
    final int defaultsLength = v1.write(defaults, 0);
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = current.write(rewritten, 0);
    assertThat(hex(rewritten, rewrittenLength)).isEqualTo(hex(defaults, defaultsLength));
    assertThat(current.getKey()).isZero();
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new SignalSubscription();
    current.setRecord(record("signal-b"));
    current.setKey(42L);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new SignalSubscriptionV1();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getRecord().getSignalName()).isEqualTo("signal-b");
    assertThat(v1.getRecord().getProcessInstanceKey()).isEqualTo(12L);
    assertThat(v1.getKey()).isEqualTo(42L);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new SignalSubscription();
    source.setRecord(record("signal-c"));
    source.setKey(1L);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // The map has two entries, so its header is 0x82; announce one more entry (0x83).
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new SignalSubscription();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setKey(2L);
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  private static SignalSubscriptionRecord record(final String signalName) {
    return new SignalSubscriptionRecord()
        .setSignalName(utf8(signalName))
        .setCatchEventInstanceKey(11L)
        .setProcessInstanceKey(12L)
        .setProcessDefinitionKey(13L)
        .setTenantId("tenant-a");
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
