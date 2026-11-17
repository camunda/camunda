/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.record.value.JobRecordValue.JobResultActivateElementValue;
import io.camunda.zeebe.protocol.record.value.JobResultType;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link io.camunda.zeebe.protocol.impl.record.value.job.legacy.JobResult} is a frozen copy
 * of the property based implementation the generated codec replaced.
 */
final class JobResultSerdeTest {

  private static final String DENIED_REASON = "a denied reason that is longer than forty bytes";
  private static final List<String> CORRECTED_ATTRIBUTES = List.of("assignee", "priority");
  private static final DirectBuffer VARIABLES =
      new UnsafeBuffer(MsgPackConverter.convertToMsgPack("{\"a\":70000}"));

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new io.camunda.zeebe.protocol.impl.record.value.job.legacy.JobResult();
    final var current = new JobResult();
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
    final var v1 = new io.camunda.zeebe.protocol.impl.record.value.job.legacy.JobResult();
    final var current = new JobResult();

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
    final var v1 = new io.camunda.zeebe.protocol.impl.record.value.job.legacy.JobResult();
    setAllProperties(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new JobResult();
    setDirtyProperties(current);

    // when
    current.wrap(bytes, 0, length);

    // then
    assertAllProperties(current);
  }

  @Test
  void shouldRestoreDefaultsWhenReadingBytesWrittenByV1WithDefaults() {
    // given a value with defaults everywhere
    final var v1 = new io.camunda.zeebe.protocol.impl.record.value.job.legacy.JobResult();
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new JobResult();
    setAllProperties(current);

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getType()).isEqualTo(v1.getType()).isEqualTo(JobResultType.USER_TASK);
    assertThat(current.isDenied()).isEqualTo(v1.isDenied()).isFalse();
    assertThat(current.getDeniedReason()).isEqualTo(v1.getDeniedReason()).isEmpty();
    assertThat(current.getCorrectedAttributes()).isEqualTo(v1.getCorrectedAttributes()).isEmpty();
    assertThat(current.getCorrections().getAssignee()).isEmpty();
    assertThat(current.getCorrections().getDueDate()).isEmpty();
    assertThat(current.getCorrections().getFollowUpDate()).isEmpty();
    assertThat(current.getCorrections().getCandidateUsersList()).isEmpty();
    assertThat(current.getCorrections().getCandidateGroupsList()).isEmpty();
    assertThat(current.getCorrections().getPriority())
        .isEqualTo(v1.getCorrections().getPriority())
        .isEqualTo(-1);
    assertThat(current.getActivateElements()).isEqualTo(v1.getActivateElements()).isEmpty();
    assertThat(current.isCompletionConditionFulfilled())
        .isEqualTo(v1.isCompletionConditionFulfilled())
        .isFalse();
    assertThat(current.isCancelRemainingInstances())
        .isEqualTo(v1.isCancelRemainingInstances())
        .isFalse();
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new JobResult();
    setAllProperties(current);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new io.camunda.zeebe.protocol.impl.record.value.job.legacy.JobResult();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getType()).isEqualTo(JobResultType.AD_HOC_SUB_PROCESS);
    assertThat(v1.isDenied()).isTrue();
    assertThat(v1.getDeniedReason()).isEqualTo(DENIED_REASON);
    assertThat(v1.getCorrectedAttributes()).containsExactlyElementsOf(CORRECTED_ATTRIBUTES);
    assertThat(v1.getCorrections().getAssignee()).isEqualTo("an-assignee");
    assertThat(v1.getCorrections().getDueDate()).isEqualTo("a-due-date");
    assertThat(v1.getCorrections().getFollowUpDate()).isEqualTo("a-follow-up-date");
    assertThat(v1.getCorrections().getCandidateUsersList()).containsExactly("user-a", "user-b");
    assertThat(v1.getCorrections().getCandidateGroupsList()).containsExactly("group-a");
    assertThat(v1.getCorrections().getPriority()).isEqualTo(70_000);
    assertThat(v1.getActivateElements())
        .extracting(JobResultActivateElementValue::getElementId)
        .containsExactly("element-a", "element-b", "element-c");
    assertThat(v1.getActivateElements().get(1).getVariables()).containsExactly(entry("a", 70_000));
    assertThat(v1.isCompletionConditionFulfilled()).isTrue();
    assertThat(v1.isCancelRemainingInstances()).isTrue();
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new JobResult();
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new JobResult();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setDenied(true);
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  @Test
  void shouldProduceTheSameJsonAsV1() throws Exception {
    // given
    final var v1 = new io.camunda.zeebe.protocol.impl.record.value.job.legacy.JobResult();
    final var current = new JobResult();
    setAllProperties(v1);
    setAllProperties(current);

    // when
    final var mapper = new ObjectMapper();
    final var v1Json = mapper.readTree(mapper.writeValueAsString(v1));
    final var currentJson = mapper.readTree(mapper.writeValueAsString(current));

    // then the parent record serializes the value with Jackson, so the documents must be equal
    assertThat(currentJson).isEqualTo(v1Json);
  }

  @Test
  void shouldProduceTheSameJsonAsV1WithDefaults() throws Exception {
    // given
    final var v1 = new io.camunda.zeebe.protocol.impl.record.value.job.legacy.JobResult();
    final var current = new JobResult();

    // when
    final var mapper = new ObjectMapper();
    final var v1Json = mapper.readTree(mapper.writeValueAsString(v1));
    final var currentJson = mapper.readTree(mapper.writeValueAsString(current));

    // then
    assertThat(currentJson).isEqualTo(v1Json);
  }

  @Test
  void shouldDetachActivateElementsOnWrapAndGet() {
    // given
    final var source = new JobResult();
    setAllProperties(source);

    // when
    final var target = new JobResult();
    target.wrap(source);
    final var elements = target.getActivateElements();
    source.setActivateElements(List.of());

    // then neither the target nor a returned element shares state with the source
    assertThat(source.getActivateElements()).isEmpty();
    assertThat(target.getActivateElements())
        .extracting(JobResultActivateElementValue::getElementId)
        .containsExactly("element-a", "element-b", "element-c");
    assertThat(elements).hasSize(3);
    assertThat(target.getCorrections()).isNotSameAs(source.getCorrections());
    assertThat(target.getCorrections().getAssignee()).isEqualTo("an-assignee");
  }

  private static void setAllProperties(
      final io.camunda.zeebe.protocol.impl.record.value.job.legacy.JobResult x) {
    x.setType(JobResultType.AD_HOC_SUB_PROCESS)
        .setDenied(true)
        .setDeniedReason(DENIED_REASON)
        .setCorrectedAttributes(CORRECTED_ATTRIBUTES)
        .setCorrections(
            new io.camunda.zeebe.protocol.impl.record.value.job.legacy.JobResultCorrections()
                .setAssignee("an-assignee")
                .setDueDate("a-due-date")
                .setFollowUpDate("a-follow-up-date")
                .setCandidateUsersList(List.of("user-a", "user-b"))
                .setCandidateGroupsList(List.of("group-a"))
                .setPriority(70_000))
        .addActivateElement(
            new io.camunda.zeebe.protocol.impl.record.value.job.legacy.JobResultActivateElement()
                .setElementId("element-a"))
        .addActivateElement(
            new io.camunda.zeebe.protocol.impl.record.value.job.legacy.JobResultActivateElement()
                .setElementId("element-b")
                .setVariables(VARIABLES))
        .addActivateElement(
            new io.camunda.zeebe.protocol.impl.record.value.job.legacy.JobResultActivateElement()
                .setElementId("element-c"))
        .setCompletionConditionFulfilled(true)
        .setCancelRemainingInstances(true);
  }

  private static void setAllProperties(final JobResult x) {
    x.setType(JobResultType.AD_HOC_SUB_PROCESS)
        .setDenied(true)
        .setDeniedReason(DENIED_REASON)
        .setCorrectedAttributes(CORRECTED_ATTRIBUTES)
        .setCorrections(
            new JobResultCorrections()
                .setAssignee("an-assignee")
                .setDueDate("a-due-date")
                .setFollowUpDate("a-follow-up-date")
                .setCandidateUsersList(List.of("user-a", "user-b"))
                .setCandidateGroupsList(List.of("group-a"))
                .setPriority(70_000))
        .addActivateElement(new JobResultActivateElement().setElementId("element-a"))
        .addActivateElement(
            new JobResultActivateElement().setElementId("element-b").setVariables(VARIABLES))
        .addActivateElement(new JobResultActivateElement().setElementId("element-c"))
        .setCompletionConditionFulfilled(true)
        .setCancelRemainingInstances(true);
  }

  private static void setDirtyProperties(final JobResult x) {
    x.setType(JobResultType.AD_HOC_SUB_PROCESS)
        .setDenied(true)
        .setDeniedReason("dirty")
        .setCorrectedAttributes(List.of("dirty"))
        .setCorrections(
            new JobResultCorrections()
                .setAssignee("dirty")
                .setDueDate("dirty")
                .setFollowUpDate("dirty")
                .setCandidateUsersList(List.of("dirty"))
                .setCandidateGroupsList(List.of("dirty"))
                .setPriority(1))
        .addActivateElement(new JobResultActivateElement().setElementId("dirty"))
        .setCompletionConditionFulfilled(true)
        .setCancelRemainingInstances(true);
  }

  private static void assertAllProperties(final JobResult x) {
    assertThat(x.getType()).isEqualTo(JobResultType.AD_HOC_SUB_PROCESS);
    assertThat(x.isDenied()).isTrue();
    assertThat(x.getDeniedReason()).isEqualTo(DENIED_REASON);
    assertThat(x.getCorrectedAttributes()).containsExactlyElementsOf(CORRECTED_ATTRIBUTES);
    assertThat(x.getCorrections().getAssignee()).isEqualTo("an-assignee");
    assertThat(x.getCorrections().getDueDate()).isEqualTo("a-due-date");
    assertThat(x.getCorrections().getFollowUpDate()).isEqualTo("a-follow-up-date");
    assertThat(x.getCorrections().getCandidateUsersList()).containsExactly("user-a", "user-b");
    assertThat(x.getCorrections().getCandidateGroupsList()).containsExactly("group-a");
    assertThat(x.getCorrections().getPriority()).isEqualTo(70_000);
    assertThat(x.getActivateElements())
        .extracting(JobResultActivateElementValue::getElementId)
        .containsExactly("element-a", "element-b", "element-c");
    assertThat(x.getActivateElements().get(1).getVariables()).containsExactly(entry("a", 70_000));
    assertThat(x.isCompletionConditionFulfilled()).isTrue();
    assertThat(x.isCancelRemainingInstances()).isTrue();
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
