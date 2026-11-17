/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
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
 * break. {@link io.camunda.zeebe.protocol.impl.record.value.job.legacy.JobResultCorrections} is a
 * frozen copy of the property based implementation the generated codec replaced.
 */
final class JobResultCorrectionsSerdeTest {

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.job.legacy.JobResultCorrections();
    final var current = new JobResultCorrections();
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
    // given only defaults, every property has one
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.job.legacy.JobResultCorrections();
    final var current = new JobResultCorrections();

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
        new io.camunda.zeebe.protocol.impl.record.value.job.legacy.JobResultCorrections();
    setAllProperties(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new JobResultCorrections();
    current
        .setAssignee("dirty")
        .setDueDate("dirty")
        .setFollowUpDate("dirty")
        .setCandidateUsersList(List.of("dirty"))
        .setCandidateGroupsList(List.of("dirty"))
        .setPriority(1);

    // when
    current.wrap(bytes, 0, length);

    // then
    assertAllProperties(current);
  }

  @Test
  void shouldRestoreDefaultsWhenReadingBytesWrittenByV1WithDefaults() {
    // given a value with defaults everywhere
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.job.legacy.JobResultCorrections();
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new JobResultCorrections();
    setAllProperties(current);

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getAssignee()).isEqualTo(v1.getAssignee());
    assertThat(current.getDueDate()).isEqualTo(v1.getDueDate());
    assertThat(current.getFollowUpDate()).isEqualTo(v1.getFollowUpDate());
    assertThat(current.getCandidateUsersList()).isEqualTo(v1.getCandidateUsersList()).isEmpty();
    assertThat(current.getCandidateGroupsList()).isEqualTo(v1.getCandidateGroupsList()).isEmpty();
    assertThat(current.getPriority()).isEqualTo(v1.getPriority()).isEqualTo(-1);
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new JobResultCorrections();
    setAllProperties(current);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.job.legacy.JobResultCorrections();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getAssignee()).isEqualTo(ASSIGNEE);
    assertThat(v1.getDueDate()).isEqualTo(DUE_DATE);
    assertThat(v1.getFollowUpDate()).isEqualTo(FOLLOW_UP_DATE);
    assertThat(v1.getCandidateUsersList()).containsExactlyElementsOf(USERS);
    assertThat(v1.getCandidateGroupsList()).containsExactlyElementsOf(GROUPS);
    assertThat(v1.getPriority()).isEqualTo(PRIORITY);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new JobResultCorrections();
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new JobResultCorrections();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setPriority(5);
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
        new io.camunda.zeebe.protocol.impl.record.value.job.legacy.JobResultCorrections();
    final var current = new JobResultCorrections();
    setAllProperties(v1);
    setAllProperties(current);

    // when
    final var mapper = new ObjectMapper();
    final var v1Json = mapper.readTree(mapper.writeValueAsString(v1));
    final var currentJson = mapper.readTree(mapper.writeValueAsString(current));

    // then the parent record serializes the value with Jackson, so the documents must be equal
    assertThat(currentJson).isEqualTo(v1Json);
  }

  private static final String ASSIGNEE = "an-assignee-that-is-longer-than-thirty-two-bytes";
  private static final String DUE_DATE = "2030-01-01T00:00:00Z";
  private static final String FOLLOW_UP_DATE = "2030-02-01T00:00:00Z";
  // 20 entries change the msgpack array header
  private static final List<String> USERS =
      IntStream.range(0, 20).mapToObj(i -> "user-" + i).toList();
  private static final List<String> GROUPS = List.of("group-a", "group-b");
  private static final int PRIORITY = 70_000;

  private static void setAllProperties(
      final io.camunda.zeebe.protocol.impl.record.value.job.legacy.JobResultCorrections x) {
    x.setAssignee(ASSIGNEE)
        .setDueDate(DUE_DATE)
        .setFollowUpDate(FOLLOW_UP_DATE)
        .setCandidateUsersList(USERS)
        .setCandidateGroupsList(GROUPS)
        .setPriority(PRIORITY);
  }

  private static void setAllProperties(final JobResultCorrections x) {
    x.setAssignee(ASSIGNEE)
        .setDueDate(DUE_DATE)
        .setFollowUpDate(FOLLOW_UP_DATE)
        .setCandidateUsersList(USERS)
        .setCandidateGroupsList(GROUPS)
        .setPriority(PRIORITY);
  }

  private static void assertAllProperties(final JobResultCorrections x) {
    assertThat(x.getAssignee()).isEqualTo(ASSIGNEE);
    assertThat(x.getDueDate()).isEqualTo(DUE_DATE);
    assertThat(x.getFollowUpDate()).isEqualTo(FOLLOW_UP_DATE);
    assertThat(x.getCandidateUsersList()).containsExactlyElementsOf(USERS);
    assertThat(x.getCandidateGroupsList()).containsExactlyElementsOf(GROUPS);
    assertThat(x.getPriority()).isEqualTo(PRIORITY);
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
