/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.globallistener;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.RecordV1s;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.impl.record.value.globallistener.GlobalListenerRecord;
import io.camunda.zeebe.protocol.record.value.GlobalListenerSource;
import io.camunda.zeebe.protocol.record.value.GlobalListenerType;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link io.camunda.zeebe.engine.state.globallistener.legacy.PersistedGlobalListener} is a
 * frozen copy of the property based implementation the generated codec replaced.
 */
final class PersistedGlobalListenerSerdeTest {

  // longer than 31 bytes, so the msgpack string header changes
  private static final String LONG_ID = "a-listener-id-longer-than-thirty-one-bytes";

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 =
        new io.camunda.zeebe.engine.state.globallistener.legacy.PersistedGlobalListener();
    v1.setGlobalListener(
        RecordV1s.convert(
            populatedRecord(),
            new io.camunda.zeebe.protocol.impl.record.value.globallistener.legacy
                .GlobalListenerRecord()));
    final var current = new PersistedGlobalListener();
    current.setGlobalListener(populatedRecord());

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
    // given
    final var v1 =
        new io.camunda.zeebe.engine.state.globallistener.legacy.PersistedGlobalListener();
    final var current = new PersistedGlobalListener();

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
        new io.camunda.zeebe.engine.state.globallistener.legacy.PersistedGlobalListener();
    v1.setGlobalListener(
        RecordV1s.convert(
            populatedRecord(),
            new io.camunda.zeebe.protocol.impl.record.value.globallistener.legacy
                .GlobalListenerRecord()));
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new PersistedGlobalListener();
    current.setGlobalListener(new GlobalListenerRecord().setId("dirty").setRetries(9));

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getGlobalListener().getGlobalListenerKey()).isEqualTo(12L);
    assertThat(current.getGlobalListener().getId()).isEqualTo(LONG_ID);
    assertThat(current.getGlobalListener().getRetries()).isEqualTo(5);
    assertThat(current.getGlobalListener().getEventTypes()).containsExactly("all", "custom");
    assertThat(current.getGlobalListener().getConfigKey()).isEqualTo(34L);
  }

  @Test
  void shouldReadTheDefaultsWhenBytesHoldTheDefaults() {
    // given
    final var bytes = new ExpandableArrayBuffer();
    final int length =
        new io.camunda.zeebe.engine.state.globallistener.legacy.PersistedGlobalListener()
            .write(bytes, 0);
    final var current = new PersistedGlobalListener();
    current.setGlobalListener(populatedRecord());

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getGlobalListener().getId()).isEmpty();
    assertThat(current.getGlobalListener().getRetries())
        .isEqualTo(GlobalListenerRecord.DEFAULT_RETRIES);
    assertThat(current.getGlobalListener().getEventTypes()).isEmpty();
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new PersistedGlobalListener();
    current.setGlobalListener(populatedRecord());
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 =
        new io.camunda.zeebe.engine.state.globallistener.legacy.PersistedGlobalListener();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getGlobalListener().getId()).isEqualTo(LONG_ID);
    assertThat(v1.getGlobalListener().getEventTypes()).containsExactly("all", "custom");
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new PersistedGlobalListener();
    source.setGlobalListener(populatedRecord());
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // One entry: the map header is 0x80 | count, so announce one more entry.
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new PersistedGlobalListener();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setGlobalListener(new GlobalListenerRecord().setId("other"));
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  private static GlobalListenerRecord populatedRecord() {
    return new GlobalListenerRecord()
        .setGlobalListenerKey(12L)
        .setId(LONG_ID)
        .setType("custom-type")
        .setRetries(5)
        .setEventTypes(List.of("all", "custom"))
        .setAfterNonGlobal(true)
        .setPriority(70)
        .setSource(GlobalListenerSource.API)
        .setListenerType(GlobalListenerType.USER_TASK)
        .setConfigKey(34L);
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
