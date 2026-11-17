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
import static org.assertj.core.api.Assertions.tuple;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.protocol.record.value.JobListenerEventType;
import io.camunda.zeebe.protocol.record.value.JobRecordValue.JobResultActivateElementValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue.JobSecretReferenceValue;
import io.camunda.zeebe.protocol.record.value.JobResultType;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link io.camunda.zeebe.protocol.impl.record.value.job.legacy.JobRecord} is a frozen copy
 * of the property based implementation the generated codec replaced.
 */
final class JobRecordSerdeTest {

  private static final String WORKER_STRING = "a-worker-name-that-is-longer-than-thirty-two-bytes";
  private static final DirectBuffer WORKER = utf8(WORKER_STRING);
  private static final String TYPE = "a-job-type-that-is-longer-than-thirty-two-bytes";
  private static final String ERROR_MESSAGE = "an error message that is longer than forty bytes";
  private static final String BPMN_PROCESS_ID = "a-process-id-that-is-longer-than-thirty-two-bytes";
  private static final DirectBuffer CUSTOM_HEADERS =
      new UnsafeBuffer(MsgPackConverter.convertToMsgPack(Map.of("header", "value")));
  private static final DirectBuffer VARIABLES =
      new UnsafeBuffer(MsgPackConverter.convertToMsgPack(Map.of("variable", 1)));
  private static final Set<String> CHANGED_ATTRIBUTES = Set.of("assignee", "dueDate");
  private static final Set<String> TAGS = Set.of("a-tag", "another-tag");
  private static final DirectBuffer ELEMENT_VARIABLES =
      new UnsafeBuffer(MsgPackConverter.convertToMsgPack(Map.of("elementVariable", 70_000)));
  private static final JobResult RESULT =
      new JobResult()
          .setType(JobResultType.AD_HOC_SUB_PROCESS)
          .setDenied(true)
          .setDeniedReason("a reason")
          .setCorrectedAttributes(List.of("assignee", "priority"))
          .setCorrections(
              new JobResultCorrections()
                  .setAssignee("an-assignee-that-is-longer-than-thirty-two-bytes")
                  .setDueDate("2030-01-01T00:00:00Z")
                  .setFollowUpDate("2030-02-01T00:00:00Z")
                  .setCandidateUsersList(List.of("user-a", "user-b"))
                  .setCandidateGroupsList(List.of("group-a", "group-b"))
                  .setPriority(70_000))
          .addActivateElement(new JobResultActivateElement().setElementId("element-a"))
          .addActivateElement(
              new JobResultActivateElement()
                  .setElementId("element-b")
                  .setVariables(ELEMENT_VARIABLES))
          .setCompletionConditionFulfilled(true)
          .setCancelRemainingInstances(true);
  private static final io.camunda.zeebe.protocol.impl.record.value.job.legacy.JobResult
      LEGACY_RESULT =
          new io.camunda.zeebe.protocol.impl.record.value.job.legacy.JobResult()
              .setType(JobResultType.AD_HOC_SUB_PROCESS)
              .setDenied(true)
              .setDeniedReason("a reason")
              .setCorrectedAttributes(List.of("assignee", "priority"))
              .setCorrections(
                  new io.camunda.zeebe.protocol.impl.record.value.job.legacy.JobResultCorrections()
                      .setAssignee("an-assignee-that-is-longer-than-thirty-two-bytes")
                      .setDueDate("2030-01-01T00:00:00Z")
                      .setFollowUpDate("2030-02-01T00:00:00Z")
                      .setCandidateUsersList(List.of("user-a", "user-b"))
                      .setCandidateGroupsList(List.of("group-a", "group-b"))
                      .setPriority(70_000))
              .addActivateElement(
                  new io.camunda.zeebe.protocol.impl.record.value.job.legacy
                          .JobResultActivateElement()
                      .setElementId("element-a"))
              .addActivateElement(
                  new io.camunda.zeebe.protocol.impl.record.value.job.legacy
                          .JobResultActivateElement()
                      .setElementId("element-b")
                      .setVariables(ELEMENT_VARIABLES))
              .setCompletionConditionFulfilled(true)
              .setCancelRemainingInstances(true);

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new io.camunda.zeebe.protocol.impl.record.value.job.legacy.JobRecord();
    final var current = new JobRecord();
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
    // given everything stays default
    final var v1 = new io.camunda.zeebe.protocol.impl.record.value.job.legacy.JobRecord();
    final var current = new JobRecord();

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
    final var v1 = new io.camunda.zeebe.protocol.impl.record.value.job.legacy.JobRecord();
    setAllProperties(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new JobRecord();
    current
        .setDeadline(1L)
        .setWorker("dirty")
        .setType("dirty")
        .setCustomHeaders(CUSTOM_HEADERS)
        .setVariables(VARIABLES)
        .setJobKind(JobKind.EXECUTION_LISTENER)
        .setChangedAttributes(Set.of("dirty"))
        .setTags(Set.of("dirty"))
        .setIsJobToUserTaskMigration(true)
        .setStorageOrdinal(5)
        .setResult(
            new JobResult()
                .setDenied(true)
                .setCorrectedAttributes(List.of("dirty"))
                .addActivateElement(new JobResultActivateElement().setElementId("dirty")))
        .addSecretReference("dirty", "dirty", "dirty");

    // when
    current.wrap(bytes, 0, length);

    // then
    assertAllProperties(current);
  }

  @Test
  void shouldRestoreDefaultsWhenReadingBytesWrittenByV1WithDefaults() {
    // given a value with defaults everywhere
    final var v1 = new io.camunda.zeebe.protocol.impl.record.value.job.legacy.JobRecord();
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new JobRecord();
    setAllProperties(current);

    // when
    current.wrap(bytes, 0, length);

    // then
    assertDefaultsOf(
        new io.camunda.zeebe.protocol.impl.record.value.job.legacy.JobRecord(), current);
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new JobRecord();
    setAllProperties(current);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new io.camunda.zeebe.protocol.impl.record.value.job.legacy.JobRecord();
    v1.wrap(bytes, 0, length);

    // then
    assertAllProperties(v1);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new JobRecord();
    setAllProperties(source);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // the map has 31 entries, plus one unknown: 0xde 0x00 0x1f becomes 0xde 0x00 0x20
    stored.putByte(2, (byte) (stored.getByte(2) + 1));

    // when read, modified and written again
    final var instance = new JobRecord();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setStorageOrdinal(9);
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  @Test
  void shouldProduceTheSameJsonAsV1() throws Exception {
    // given
    final var v1 = new io.camunda.zeebe.protocol.impl.record.value.job.legacy.JobRecord();
    final var current = new JobRecord();
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
      final io.camunda.zeebe.protocol.impl.record.value.job.legacy.JobRecord record) {
    record
        .setDeadline(4_294_967_296L)
        .setTimeout(65_536L)
        .setWorker(WORKER)
        .setRetries(300)
        .setRetryBackoff(70_000L)
        .setRecurringTime(131_072L)
        .setType(TYPE)
        .setCustomHeaders(CUSTOM_HEADERS)
        .setVariables(VARIABLES)
        .setErrorMessage(ERROR_MESSAGE)
        .setErrorCode(utf8("an-error-code"))
        .setBpmnProcessId(BPMN_PROCESS_ID)
        .setProcessDefinitionVersion(70_000)
        .setProcessDefinitionKey(8_589_934_592L)
        .setProcessInstanceKey(12_884_901_888L)
        .setJobKind(JobKind.TASK_LISTENER)
        .setListenerEventType(JobListenerEventType.COMPLETING)
        .setElementId("an-element-id-that-is-longer-than-thirty-two-bytes")
        .setElementType(BpmnElementType.USER_TASK)
        .setElementInstanceKey(17_179_869_184L)
        .setTenantId("a-tenant")
        .setChangedAttributes(CHANGED_ATTRIBUTES)
        .setResult(LEGACY_RESULT)
        .setTags(TAGS)
        .setIsJobToUserTaskMigration(true)
        .setRootProcessInstanceKey(21_474_836_480L)
        .setStorageOrdinal(3)
        .setPriority(70_001)
        .setBusinessId("a-business-id-that-is-longer-than-thirty-two-bytes")
        .setJobLeaseToken("a-lease-token")
        .addSecretReference("a-store", "a-secret", "/secret/path")
        .addSecretReference("another-store", "another-secret", "/other/path");
  }

  private static void setAllProperties(final JobRecord record) {
    record
        .setDeadline(4_294_967_296L)
        .setTimeout(65_536L)
        .setWorker(WORKER)
        .setRetries(300)
        .setRetryBackoff(70_000L)
        .setRecurringTime(131_072L)
        .setType(TYPE)
        .setCustomHeaders(CUSTOM_HEADERS)
        .setVariables(VARIABLES)
        .setErrorMessage(ERROR_MESSAGE)
        .setErrorCode(utf8("an-error-code"))
        .setBpmnProcessId(BPMN_PROCESS_ID)
        .setProcessDefinitionVersion(70_000)
        .setProcessDefinitionKey(8_589_934_592L)
        .setProcessInstanceKey(12_884_901_888L)
        .setJobKind(JobKind.TASK_LISTENER)
        .setListenerEventType(JobListenerEventType.COMPLETING)
        .setElementId("an-element-id-that-is-longer-than-thirty-two-bytes")
        .setElementType(BpmnElementType.USER_TASK)
        .setElementInstanceKey(17_179_869_184L)
        .setTenantId("a-tenant")
        .setChangedAttributes(CHANGED_ATTRIBUTES)
        .setResult(RESULT)
        .setTags(TAGS)
        .setIsJobToUserTaskMigration(true)
        .setRootProcessInstanceKey(21_474_836_480L)
        .setStorageOrdinal(3)
        .setPriority(70_001)
        .setBusinessId("a-business-id-that-is-longer-than-thirty-two-bytes")
        .setJobLeaseToken("a-lease-token")
        .addSecretReference("a-store", "a-secret", "/secret/path")
        .addSecretReference("another-store", "another-secret", "/other/path");
  }

  private static void assertAllProperties(
      final io.camunda.zeebe.protocol.impl.record.value.job.legacy.JobRecord record) {
    assertThat(record.getDeadline()).isEqualTo(4_294_967_296L);
    assertThat(record.getTimeout()).isEqualTo(65_536L);
    assertThat(record.getWorker()).isEqualTo(WORKER_STRING);
    assertThat(record.getRetries()).isEqualTo(300);
    assertThat(record.getRetryBackoff()).isEqualTo(70_000L);
    assertThat(record.getRecurringTime()).isEqualTo(131_072L);
    assertThat(record.getType()).isEqualTo(TYPE);
    assertThat(record.getCustomHeaders()).containsExactly(entry("header", "value"));
    assertThat(record.getVariables()).containsExactly(entry("variable", 1));
    assertThat(record.getErrorMessage()).isEqualTo(ERROR_MESSAGE);
    assertThat(record.getErrorCode()).isEqualTo("an-error-code");
    assertThat(record.getBpmnProcessId()).isEqualTo(BPMN_PROCESS_ID);
    assertThat(record.getProcessDefinitionVersion()).isEqualTo(70_000);
    assertThat(record.getProcessDefinitionKey()).isEqualTo(8_589_934_592L);
    assertThat(record.getProcessInstanceKey()).isEqualTo(12_884_901_888L);
    assertThat(record.getJobKind()).isEqualTo(JobKind.TASK_LISTENER);
    assertThat(record.getJobListenerEventType()).isEqualTo(JobListenerEventType.COMPLETING);
    assertThat(record.getElementId())
        .isEqualTo("an-element-id-that-is-longer-than-thirty-two-bytes");
    assertThat(record.getElementType()).isEqualTo(BpmnElementType.USER_TASK);
    assertThat(record.getElementInstanceKey()).isEqualTo(17_179_869_184L);
    assertThat(record.getTenantId()).isEqualTo("a-tenant");
    assertThat(record.getChangedAttributes()).isEqualTo(CHANGED_ATTRIBUTES);
    assertThat(record.getResult().getType()).isEqualTo(JobResultType.AD_HOC_SUB_PROCESS);
    assertThat(record.getResult().isDenied()).isTrue();
    assertThat(record.getResult().getDeniedReason()).isEqualTo("a reason");
    assertThat(record.getResult().getCorrectedAttributes()).containsExactly("assignee", "priority");
    assertThat(record.getResult().getCorrections().getAssignee())
        .isEqualTo("an-assignee-that-is-longer-than-thirty-two-bytes");
    assertThat(record.getResult().getCorrections().getDueDate()).isEqualTo("2030-01-01T00:00:00Z");
    assertThat(record.getResult().getCorrections().getFollowUpDate())
        .isEqualTo("2030-02-01T00:00:00Z");
    assertThat(record.getResult().getCorrections().getCandidateUsersList())
        .containsExactly("user-a", "user-b");
    assertThat(record.getResult().getCorrections().getCandidateGroupsList())
        .containsExactly("group-a", "group-b");
    assertThat(record.getResult().getCorrections().getPriority()).isEqualTo(70_000);
    assertThat(record.getResult().getActivateElements())
        .extracting(JobResultActivateElementValue::getElementId)
        .containsExactly("element-a", "element-b");
    assertThat(record.getResult().getActivateElements().get(1).getVariables())
        .containsExactly(entry("elementVariable", 70_000));
    assertThat(record.getResult().isCompletionConditionFulfilled()).isTrue();
    assertThat(record.getResult().isCancelRemainingInstances()).isTrue();
    assertThat(record.getTags()).isEqualTo(TAGS);
    assertThat(record.isJobToUserTaskMigration()).isTrue();
    assertThat(record.getRootProcessInstanceKey()).isEqualTo(21_474_836_480L);
    assertThat(record.getStorageOrdinal()).isEqualTo(3);
    assertThat(record.getPriority()).isEqualTo(70_001);
    assertThat(record.getBusinessId())
        .isEqualTo("a-business-id-that-is-longer-than-thirty-two-bytes");
    assertThat(record.getJobLeaseToken()).isEqualTo("a-lease-token");
    assertThat(record.getSecretReferences())
        .extracting(
            JobSecretReferenceValue::getStoreId,
            JobSecretReferenceValue::getSecretReference,
            JobSecretReferenceValue::getPath)
        .containsExactly(
            tuple("a-store", "a-secret", "/secret/path"),
            tuple("another-store", "another-secret", "/other/path"));
  }

  private static void assertAllProperties(final JobRecord record) {
    assertThat(record.getDeadline()).isEqualTo(4_294_967_296L);
    assertThat(record.getTimeout()).isEqualTo(65_536L);
    assertThat(record.getWorker()).isEqualTo(WORKER_STRING);
    assertThat(record.getRetries()).isEqualTo(300);
    assertThat(record.getRetryBackoff()).isEqualTo(70_000L);
    assertThat(record.getRecurringTime()).isEqualTo(131_072L);
    assertThat(record.getType()).isEqualTo(TYPE);
    assertThat(record.getCustomHeaders()).containsExactly(entry("header", "value"));
    assertThat(record.getVariables()).containsExactly(entry("variable", 1));
    assertThat(record.getErrorMessage()).isEqualTo(ERROR_MESSAGE);
    assertThat(record.getErrorCode()).isEqualTo("an-error-code");
    assertThat(record.getBpmnProcessId()).isEqualTo(BPMN_PROCESS_ID);
    assertThat(record.getProcessDefinitionVersion()).isEqualTo(70_000);
    assertThat(record.getProcessDefinitionKey()).isEqualTo(8_589_934_592L);
    assertThat(record.getProcessInstanceKey()).isEqualTo(12_884_901_888L);
    assertThat(record.getJobKind()).isEqualTo(JobKind.TASK_LISTENER);
    assertThat(record.getJobListenerEventType()).isEqualTo(JobListenerEventType.COMPLETING);
    assertThat(record.getElementId())
        .isEqualTo("an-element-id-that-is-longer-than-thirty-two-bytes");
    assertThat(record.getElementType()).isEqualTo(BpmnElementType.USER_TASK);
    assertThat(record.getElementInstanceKey()).isEqualTo(17_179_869_184L);
    assertThat(record.getTenantId()).isEqualTo("a-tenant");
    assertThat(record.getChangedAttributes()).isEqualTo(CHANGED_ATTRIBUTES);
    assertThat(record.getResult().getType()).isEqualTo(JobResultType.AD_HOC_SUB_PROCESS);
    assertThat(record.getResult().isDenied()).isTrue();
    assertThat(record.getResult().getDeniedReason()).isEqualTo("a reason");
    assertThat(record.getResult().getCorrectedAttributes()).containsExactly("assignee", "priority");
    assertThat(record.getResult().getCorrections().getAssignee())
        .isEqualTo("an-assignee-that-is-longer-than-thirty-two-bytes");
    assertThat(record.getResult().getCorrections().getDueDate()).isEqualTo("2030-01-01T00:00:00Z");
    assertThat(record.getResult().getCorrections().getFollowUpDate())
        .isEqualTo("2030-02-01T00:00:00Z");
    assertThat(record.getResult().getCorrections().getCandidateUsersList())
        .containsExactly("user-a", "user-b");
    assertThat(record.getResult().getCorrections().getCandidateGroupsList())
        .containsExactly("group-a", "group-b");
    assertThat(record.getResult().getCorrections().getPriority()).isEqualTo(70_000);
    assertThat(record.getResult().getActivateElements())
        .extracting(JobResultActivateElementValue::getElementId)
        .containsExactly("element-a", "element-b");
    assertThat(record.getResult().getActivateElements().get(1).getVariables())
        .containsExactly(entry("elementVariable", 70_000));
    assertThat(record.getResult().isCompletionConditionFulfilled()).isTrue();
    assertThat(record.getResult().isCancelRemainingInstances()).isTrue();
    assertThat(record.getTags()).isEqualTo(TAGS);
    assertThat(record.isJobToUserTaskMigration()).isTrue();
    assertThat(record.getRootProcessInstanceKey()).isEqualTo(21_474_836_480L);
    assertThat(record.getStorageOrdinal()).isEqualTo(3);
    assertThat(record.getPriority()).isEqualTo(70_001);
    assertThat(record.getBusinessId())
        .isEqualTo("a-business-id-that-is-longer-than-thirty-two-bytes");
    assertThat(record.getJobLeaseToken()).isEqualTo("a-lease-token");
    assertThat(record.getSecretReferences())
        .extracting(
            JobSecretReferenceValue::getStoreId,
            JobSecretReferenceValue::getSecretReference,
            JobSecretReferenceValue::getPath)
        .containsExactly(
            tuple("a-store", "a-secret", "/secret/path"),
            tuple("another-store", "another-secret", "/other/path"));
  }

  private static void assertDefaultsOf(
      final io.camunda.zeebe.protocol.impl.record.value.job.legacy.JobRecord defaults,
      final JobRecord record) {
    assertThat(record.getDeadline()).isEqualTo(defaults.getDeadline());
    assertThat(record.getTimeout()).isEqualTo(defaults.getTimeout());
    assertThat(record.getWorker()).isEqualTo(defaults.getWorker());
    assertThat(record.getRetries()).isEqualTo(defaults.getRetries());
    assertThat(record.getRetryBackoff()).isEqualTo(defaults.getRetryBackoff());
    assertThat(record.getRecurringTime()).isEqualTo(defaults.getRecurringTime());
    assertThat(record.getType()).isEqualTo(defaults.getType());
    assertThat(record.getCustomHeaders()).isEqualTo(defaults.getCustomHeaders());
    assertThat(record.getVariables()).isEqualTo(defaults.getVariables());
    assertThat(record.getErrorMessage()).isEqualTo(defaults.getErrorMessage());
    assertThat(record.getErrorCode()).isEqualTo(defaults.getErrorCode());
    assertThat(record.getBpmnProcessId()).isEqualTo(defaults.getBpmnProcessId());
    assertThat(record.getProcessDefinitionVersion())
        .isEqualTo(defaults.getProcessDefinitionVersion());
    assertThat(record.getProcessDefinitionKey()).isEqualTo(defaults.getProcessDefinitionKey());
    assertThat(record.getProcessInstanceKey()).isEqualTo(defaults.getProcessInstanceKey());
    assertThat(record.getJobKind()).isEqualTo(defaults.getJobKind());
    assertThat(record.getJobListenerEventType()).isEqualTo(defaults.getJobListenerEventType());
    assertThat(record.getElementId()).isEqualTo(defaults.getElementId());
    assertThat(record.getElementType()).isEqualTo(defaults.getElementType());
    assertThat(record.getElementInstanceKey()).isEqualTo(defaults.getElementInstanceKey());
    assertThat(record.getTenantId()).isEqualTo(defaults.getTenantId());
    assertThat(record.getChangedAttributes()).isEqualTo(defaults.getChangedAttributes());
    assertThat(record.getTags()).isEqualTo(defaults.getTags());
    assertThat(record.getResult().getType()).isEqualTo(defaults.getResult().getType());
    assertThat(record.getResult().isDenied()).isEqualTo(defaults.getResult().isDenied());
    assertThat(record.getRootProcessInstanceKey()).isEqualTo(defaults.getRootProcessInstanceKey());
    assertThat(record.getStorageOrdinal()).isEqualTo(defaults.getStorageOrdinal());
    assertThat(record.getPriority()).isEqualTo(defaults.getPriority());
    assertThat(record.getBusinessId()).isEqualTo(defaults.getBusinessId());
    assertThat(record.getJobLeaseToken()).isEqualTo(defaults.getJobLeaseToken());
    assertThat(record.getSecretReferences()).isEqualTo(defaults.getSecretReferences());
    assertThat(record.isJobToUserTaskMigration()).isEqualTo(defaults.isJobToUserTaskMigration());
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
