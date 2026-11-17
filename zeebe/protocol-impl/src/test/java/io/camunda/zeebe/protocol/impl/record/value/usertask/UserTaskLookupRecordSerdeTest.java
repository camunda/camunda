/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.usertask;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.record.value.UserTaskLifecycleState;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.IntStream;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link io.camunda.zeebe.protocol.impl.record.value.usertask.legacy.UserTaskLookupRecord}
 * is a frozen copy of the property based implementation the generated codec replaced.
 */
final class UserTaskLookupRecordSerdeTest {

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.usertask.legacy.UserTaskLookupRecord();
    final var current = new UserTaskLookupRecord();
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
    // given every property has a default, nothing is set
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.usertask.legacy.UserTaskLookupRecord();
    final var current = new UserTaskLookupRecord();

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
        new io.camunda.zeebe.protocol.impl.record.value.usertask.legacy.UserTaskLookupRecord();
    setAllProperties(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new UserTaskLookupRecord();

    // when
    current.wrap(bytes, 0, length);

    // then
    assertAllProperties(current);
  }

  @Test
  void shouldRestoreDefaultsWhenReadingBytesWrittenByV1WithDefaults() {
    // given a value with defaults everywhere
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.usertask.legacy.UserTaskLookupRecord();
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new UserTaskLookupRecord();
    setAllProperties(current);

    // when
    current.wrap(bytes, 0, length);

    // then
    final var defaults =
        new io.camunda.zeebe.protocol.impl.record.value.usertask.legacy.UserTaskLookupRecord();
    assertDefaultsOf(defaults, current);
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new UserTaskLookupRecord();
    setAllProperties(current);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.usertask.legacy.UserTaskLookupRecord();
    v1.wrap(bytes, 0, length);

    // then
    assertAllProperties(v1);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new UserTaskLookupRecord();
    setAllProperties(source);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // the map has 4 entries, plus one unknown: 0x84 becomes 0x85
    stored.putByte(0, (byte) (stored.getByte(0) + 1));
    // when read, modified and written again
    final var instance = new UserTaskLookupRecord();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setTenantId("modified");
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
        new io.camunda.zeebe.protocol.impl.record.value.usertask.legacy.UserTaskLookupRecord();
    final var current = new UserTaskLookupRecord();
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

  private static void setAllProperties(
      final io.camunda.zeebe.protocol.impl.record.value.usertask.legacy.UserTaskLookupRecord
          record) {
    record
        .setFetchVariables(names(20))
        .setTenantId("tenant")
        .setUserTask(
            new io.camunda.zeebe.protocol.impl.record.value.usertask.legacy.UserTaskRecord()
                .setUserTaskKey(5L)
                .setAssignee("assignee"))
        .setLifecycleState(UserTaskLifecycleState.CREATED);
  }

  private static void setAllProperties(final UserTaskLookupRecord record) {
    record
        .setFetchVariables(names(20))
        .setTenantId("tenant")
        .setUserTask(new UserTaskRecord().setUserTaskKey(5L).setAssignee("assignee"))
        .setLifecycleState(UserTaskLifecycleState.CREATED);
  }

  private static void assertAllProperties(
      final io.camunda.zeebe.protocol.impl.record.value.usertask.legacy.UserTaskLookupRecord
          record) {
    assertThat(record.getFetchVariables()).isEqualTo(names(20));
    assertThat(record.getTenantId()).isEqualTo("tenant");
    assertThat(record.getUserTask().getUserTaskKey()).isEqualTo(5L);
    assertThat(record.getUserTask().getAssignee()).isEqualTo("assignee");
    assertThat(record.getLifecycleState()).isEqualTo(UserTaskLifecycleState.CREATED);
  }

  private static void assertAllProperties(final UserTaskLookupRecord record) {
    assertThat(record.getFetchVariables()).isEqualTo(names(20));
    assertThat(record.getTenantId()).isEqualTo("tenant");
    assertThat(record.getUserTask().getUserTaskKey()).isEqualTo(5L);
    assertThat(record.getUserTask().getAssignee()).isEqualTo("assignee");
    assertThat(record.getLifecycleState()).isEqualTo(UserTaskLifecycleState.CREATED);
  }

  private static void assertDefaultsOf(
      final io.camunda.zeebe.protocol.impl.record.value.usertask.legacy.UserTaskLookupRecord
          defaults,
      final UserTaskLookupRecord record) {
    assertThat(record.getFetchVariables()).isEqualTo(defaults.getFetchVariables());
    assertThat(record.getTenantId()).isEqualTo(defaults.getTenantId());
    assertThat(record.getUserTask().getUserTaskKey())
        .isEqualTo(defaults.getUserTask().getUserTaskKey());
    assertThat(record.getUserTask().getAssignee()).isEqualTo(defaults.getUserTask().getAssignee());
    assertThat(record.getLifecycleState()).isEqualTo(defaults.getLifecycleState());
  }

  private static List<String> names(final int count) {
    return IntStream.range(0, count).mapToObj(i -> "name" + i).toList();
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
