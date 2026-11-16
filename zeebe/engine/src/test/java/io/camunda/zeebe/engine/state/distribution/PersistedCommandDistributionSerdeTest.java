/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.distribution;

import static io.camunda.zeebe.auth.Authorization.AUTHORIZED_TENANTS;
import static io.camunda.zeebe.auth.Authorization.AUTHORIZED_USERNAME;
import static io.camunda.zeebe.auth.Authorization.USER_GROUPS_CLAIMS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.msgpack.MsgpackPropertyException;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.impl.encoding.AuthInfo;
import io.camunda.zeebe.protocol.impl.record.value.distribution.CommandDistributionRecord;
import io.camunda.zeebe.protocol.impl.record.value.signal.SignalRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.SignalIntent;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link PersistedCommandDistributionV1} is a frozen copy of the property based
 * implementation the generated codec replaced.
 */
final class PersistedCommandDistributionSerdeTest {

  private static final String QUEUE_ID = "a queue id that is longer than thirty-one bytes";
  private static final String SIGNAL_NAME = "a signal name that is longer than thirty-one bytes";

  private static final AuthInfo AUTH_INFO =
      AuthInfo.ofClaims(
          Map.of(
              AUTHORIZED_USERNAME,
              "demo-user-with-a-name-longer-than-thirty-one-bytes",
              AUTHORIZED_TENANTS,
              List.of("tenant-a", "tenant-b"),
              USER_GROUPS_CLAIMS,
              List.of("group-a")));

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given every property set to a non-default value
    final var v1 = new PersistedCommandDistributionV1().wrap(fullRecord());
    final var current = new PersistedCommandDistribution().wrap(fullRecord());

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
    // given a distribution without queue, command value or auth info
    final var record =
        new CommandDistributionRecord()
            .setValueType(ValueType.SIGNAL)
            .setIntent(SignalIntent.BROADCAST);
    final var v1 = new PersistedCommandDistributionV1().wrap(record);
    final var current = new PersistedCommandDistribution().wrap(record);

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
    final var v1 = new PersistedCommandDistributionV1();
    final var current = new PersistedCommandDistribution();

    // when / then
    assertThatThrownBy(() -> v1.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(MsgpackPropertyException.class);
    assertThatThrownBy(() -> current.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(MsgpackPropertyException.class);
  }

  @Test
  void shouldReadBytesWrittenByV1() {
    // given
    final var bytes = new ExpandableArrayBuffer();
    final int length = new PersistedCommandDistributionV1().wrap(fullRecord()).write(bytes, 0);
    final var v1 = new PersistedCommandDistributionV1().wrap(fullRecord());
    // dirty every property of the target
    final var current =
        new PersistedCommandDistribution()
            .wrap(
                new CommandDistributionRecord()
                    .setQueueId("dirty")
                    .setValueType(ValueType.SIGNAL)
                    .setIntent(SignalIntent.BROADCASTED)
                    .setCommandValue(new SignalRecord().setSignalName("dirty"))
                    .setAuthInfo(AuthInfo.ofClaims(Map.of(AUTHORIZED_USERNAME, "dirty"))));

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getQueueId()).contains(QUEUE_ID);
    assertThat(current.getValueType()).isEqualTo(ValueType.SIGNAL);
    assertThat(current.getIntent()).isEqualTo(SignalIntent.BROADCAST);
    assertThat(current.getAuthInfo()).isEqualTo(AUTH_INFO);
    assertThat(hexOf(current.getCommandValue())).isEqualTo(hexOf(v1.getCommandValue()));
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new PersistedCommandDistribution().wrap(fullRecord());
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new PersistedCommandDistributionV1();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getQueueId()).contains(QUEUE_ID);
    assertThat(v1.getValueType()).isEqualTo(ValueType.SIGNAL);
    assertThat(v1.getIntent()).isEqualTo(SignalIntent.BROADCAST);
    assertThat(v1.getAuthInfo()).isEqualTo(AUTH_INFO);
    assertThat(hexOf(v1.getCommandValue())).isEqualTo(hexOf(current.getCommandValue()));
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new PersistedCommandDistribution().wrap(fullRecord());
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // the map has five entries, plus one unknown: 0x85 becomes 0x86
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new PersistedCommandDistribution();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setQueueId("renamed");
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  private static CommandDistributionRecord fullRecord() {
    return new CommandDistributionRecord()
        .setQueueId(QUEUE_ID)
        .setValueType(ValueType.SIGNAL)
        .setIntent(SignalIntent.BROADCAST)
        .setCommandValue(new SignalRecord().setSignalName(SIGNAL_NAME))
        .setAuthInfo(AUTH_INFO);
  }

  private static String hexOf(final BufferWriter value) {
    final var buffer = new ExpandableArrayBuffer();
    final int length = value.write(buffer, 0);
    return hex(buffer, length);
  }

  private static DirectBuffer utf8(final String s) {
    return new UnsafeBuffer(s.getBytes(StandardCharsets.UTF_8));
  }

  private static String hex(final DirectBuffer buffer, final int length) {
    final var bytes = new byte[length];
    buffer.getBytes(0, bytes);
    return HexFormat.of().formatHex(bytes);
  }
}
