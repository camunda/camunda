/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.compensation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.engine.state.RecordV1s;
import io.camunda.zeebe.msgpack.MsgpackPropertyException;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.impl.record.value.compensation.CompensationSubscriptionRecord;
import io.camunda.zeebe.protocol.impl.record.value.compensation.CompensationSubscriptionRecordV1;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link CompensationSubscriptionV1} is a frozen copy of the property based implementation
 * the generated codec replaced.
 */
final class CompensationSubscriptionSerdeTest {

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var record = populatedRecord();
    final var v1 = new CompensationSubscriptionV1();
    final var current = new CompensationSubscription();
    v1.setRecord(RecordV1s.convert(record, new CompensationSubscriptionRecordV1()));
    v1.setKey(42L);
    current.setRecord(record);
    current.setKey(42L);

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
  void shouldRejectWritingWhileUnsetLikeV1() {
    // given the key is not set
    final var v1 = new CompensationSubscriptionV1();
    final var current = new CompensationSubscription();
    v1.setRecord(RecordV1s.convert(populatedRecord(), new CompensationSubscriptionRecordV1()));
    current.setRecord(populatedRecord());

    // then
    assertThatThrownBy(() -> v1.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(MsgpackPropertyException.class);
    assertThatThrownBy(() -> current.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(MsgpackPropertyException.class);
  }

  @Test
  void shouldReadBytesWrittenByV1() {
    // given
    final var v1 = new CompensationSubscriptionV1();
    v1.setRecord(RecordV1s.convert(populatedRecord(), new CompensationSubscriptionRecordV1()));
    v1.setKey(42L);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new CompensationSubscription();
    current.setRecord(new CompensationSubscriptionRecord().setProcessInstanceKey(1L));
    current.setKey(7L);

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getKey()).isEqualTo(42L);
    assertThat(current.getRecord().getProcessInstanceKey()).isEqualTo(9L);
    assertThat(current.getRecord().getTenantId()).isEqualTo("tenant");
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new CompensationSubscription();
    current.setRecord(populatedRecord());
    current.setKey(42L);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new CompensationSubscriptionV1();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getKey()).isEqualTo(42L);
    assertThat(v1.getRecord().getProcessInstanceKey()).isEqualTo(9L);
    assertThat(v1.getRecord().getTenantId()).isEqualTo("tenant");
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new CompensationSubscription();
    source.setRecord(populatedRecord());
    source.setKey(42L);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // The map has two entries, a header of 0x80 | count, so announce one more.
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new CompensationSubscription();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setKey(43L);
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  private static CompensationSubscriptionRecord populatedRecord() {
    return new CompensationSubscriptionRecord().setProcessInstanceKey(9L).setTenantId("tenant");
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
