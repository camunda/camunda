/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.message;

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
 * break. {@link MessageStartCorrelationKeyLockReleaseRecordV1} is a frozen copy of the property
 * based implementation the generated codec replaced.
 */
final class MessageStartCorrelationKeyLockReleaseRecordSerdeTest {

  private static final String TENANT = "a tenant id that is longer than forty bytes of utf8";

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new MessageStartCorrelationKeyLockReleaseRecordV1();
    final var current = new MessageStartCorrelationKeyLockReleaseRecord();
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
    final var v1 = new MessageStartCorrelationKeyLockReleaseRecordV1();
    final var current = new MessageStartCorrelationKeyLockReleaseRecord();

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
    final var v1 = new MessageStartCorrelationKeyLockReleaseRecordV1();
    setAllProperties(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new MessageStartCorrelationKeyLockReleaseRecord();
    current.setRequestKey(7L);
    current.addHolder().setProcessInstanceKey(9L);

    // when
    current.wrap(bytes, 0, length);

    // then every value was restored and no dirtied property survived
    assertThat(current.getRequestKey()).isEqualTo(70000L);
    assertThat(current.getHolders()).hasSize(2);
    assertThat(current.getHolders().get(0).getTenantId()).isEqualTo(TENANT);
    assertThat(current.getHolders().get(1).getProcessInstanceKey()).isEqualTo(70000L);
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = current.write(rewritten, 0);
    assertThat(hex(rewritten, rewrittenLength)).isEqualTo(hex(bytes, length));
  }

  @Test
  void shouldRestoreDefaultsWhenReadingBytesWrittenByV1WithDefaults() {
    // given a value with every property at its default
    final var v1 = new MessageStartCorrelationKeyLockReleaseRecordV1();

    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new MessageStartCorrelationKeyLockReleaseRecord();
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
    final var current = new MessageStartCorrelationKeyLockReleaseRecord();
    setAllProperties(current);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new MessageStartCorrelationKeyLockReleaseRecordV1();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getRequestKey()).isEqualTo(70000L);
    assertThat(v1.getHolders()).hasSize(2);
    assertThat(v1.getHolders().get(0).getTenantId()).isEqualTo(TENANT);
    assertThat(v1.getHolders().get(1).getProcessInstanceKey()).isEqualTo(70000L);
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = v1.write(rewritten, 0);
    assertThat(hex(rewritten, rewrittenLength)).isEqualTo(hex(bytes, length));
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new MessageStartCorrelationKeyLockReleaseRecord();
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
    final var instance = new MessageStartCorrelationKeyLockReleaseRecord();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setRequestKey(9L);
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  @Test
  void shouldProduceTheSameJsonAsV1() throws Exception {
    // given
    final var v1 = new MessageStartCorrelationKeyLockReleaseRecordV1();
    final var current = new MessageStartCorrelationKeyLockReleaseRecord();
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

  private static void setAllProperties(final MessageStartCorrelationKeyLockReleaseRecordV1 record) {
    record.setRequestKey(70000L);
    record
        .addHolder()
        .setProcessInstanceKey(1L)
        .setBpmnProcessId("process")
        .setCorrelationKey("key")
        .setTenantId(TENANT)
        .setStorageOrdinal(300);
    record.addHolder().setProcessInstanceKey(70000L).setBpmnProcessId("other");
  }

  private static void setAllProperties(final MessageStartCorrelationKeyLockReleaseRecord record) {
    record.setRequestKey(70000L);
    record
        .addHolder()
        .setProcessInstanceKey(1L)
        .setBpmnProcessId("process")
        .setCorrelationKey("key")
        .setTenantId(TENANT)
        .setStorageOrdinal(300);
    record.addHolder().setProcessInstanceKey(70000L).setBpmnProcessId("other");
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
