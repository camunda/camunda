/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.util;

import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_ACTIVATED;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_ACTIVATING;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_COMPLETED;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_TERMINATED;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ClockIntent;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.MappingRuleIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.intent.VariableDocumentIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ClockRecordValue;
import io.camunda.zeebe.protocol.record.value.GroupRecordValue;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.MappingRuleRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.RoleRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantRecordValue;
import io.camunda.zeebe.protocol.record.value.UserRecordValue;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableDocumentRecordValue;
import io.camunda.zeebe.test.util.record.ProcessInstanceRecordStream;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.function.Consumer;

public final class ZeebeAssertHelper {

  public static void assertProcessInstanceCreated() {
    assertProcessInstanceCreated((e) -> {});
  }

  public static void assertProcessInstanceCreated(final long processInstanceKey) {
    assertProcessInstanceCreated(processInstanceKey, w -> {});
  }

  public static void assertProcessInstanceCreated(
      final Consumer<ProcessInstanceRecordValue> consumer) {
    assertProcessInstanceState(ProcessInstanceIntent.ELEMENT_ACTIVATING, consumer);
  }

  public static void assertJobCreated(final String jobType) {
    assertThat(RecordingExporter.jobRecords(JobIntent.CREATED).withType(jobType).exists()).isTrue();
  }

  public static void assertJobCreated(
      final String jobType, final Consumer<JobRecordValue> consumer) {
    final JobRecordValue value =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withType(jobType)
            .findFirst()
            .map(Record::getValue)
            .orElse(null);

    assertThat(value).isNotNull();

    consumer.accept(value);
  }

  public static void assertIncidentCreated() {
    assertIncidentCreated(ignore -> {});
  }

  public static long assertIncidentCreated(final Consumer<IncidentRecordValue> requirement) {
    final var incidentRecord =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED).findFirst();
    assertThat(incidentRecord)
        .describedAs("Expect incident to be created")
        .hasValueSatisfying(incident -> requirement.accept(incident.getValue()));

    return incidentRecord.map(Record::getKey).orElseThrow();
  }

  public static void assertProcessInstanceCompleted(
      final long processInstanceKey, final Consumer<ProcessInstanceRecordValue> consumer) {
    final Record<ProcessInstanceRecordValue> record =
        RecordingExporter.processInstanceRecords(ELEMENT_COMPLETED)
            .withRecordKey(processInstanceKey)
            .findFirst()
            .orElse(null);

    assertThat(record).isNotNull();

    if (consumer != null) {
      consumer.accept(record.getValue());
    }
  }

  public static void assertProcessInstanceCompleted(final long processInstanceKey) {
    assertProcessInstanceCompleted(processInstanceKey, r -> {});
  }

  public static void assertElementActivated(final String element) {
    assertElementInState(ELEMENT_ACTIVATED, element, (e) -> {});
  }

  public static void assertElementReady(final String element) {
    assertElementInState(ELEMENT_ACTIVATING, element, (e) -> {});
  }

  public static void assertProcessInstanceCanceled(final String bpmnId) {
    assertThat(
            RecordingExporter.processInstanceRecords(ELEMENT_TERMINATED)
                .withBpmnProcessId(bpmnId)
                .withElementId(bpmnId)
                .exists())
        .isTrue();
  }

  public static void assertProcessInstanceCompleted(
      final String process, final long processInstanceKey) {
    assertElementCompleted(processInstanceKey, process, (e) -> {});
  }

  public static void assertProcessInstanceCompleted(final String bpmnId) {
    assertProcessInstanceCompleted(bpmnId, (e) -> {});
  }

  public static void assertProcessInstanceCompleted(
      final String bpmnId, final Consumer<ProcessInstanceRecordValue> eventConsumer) {
    assertElementCompleted(bpmnId, bpmnId, eventConsumer);
  }

  public static void assertJobCompleted() {
    assertThat(RecordingExporter.jobRecords(JobIntent.COMPLETED).exists()).isTrue();
  }

  public static void assertJobCanceled() {
    assertThat(RecordingExporter.jobRecords(JobIntent.CANCELED).exists()).isTrue();
  }

  public static void assertJobCompleted(final String jobType) {
    assertJobCompleted(jobType, (j) -> {});
  }

  public static void assertJobCompleted(
      final String jobType, final Consumer<JobRecordValue> consumer) {
    final JobRecordValue job =
        RecordingExporter.jobRecords(JobIntent.COMPLETED)
            .withType(jobType)
            .findFirst()
            .map(Record::getValue)
            .orElse(null);

    assertThat(job).isNotNull();
    consumer.accept(job);
  }

  public static void assertUserTaskCompleted(
      final long userTaskKey, final Consumer<UserTaskRecordValue> consumer) {
    final UserTaskRecordValue userTask =
        RecordingExporter.userTaskRecords(UserTaskIntent.COMPLETED)
            .filter(record -> record.getKey() == userTaskKey)
            .findFirst()
            .map(Record::getValue)
            .orElse(null);

    assertThat(userTask).isNotNull();
    consumer.accept(userTask);
  }

  public static void assertUserTaskAssigned(
      final long userTaskKey, final Consumer<UserTaskRecordValue> consumer) {
    assertUserTaskAssigned(userTaskKey, 1, consumer);
  }

  public static void assertUserTaskAssigned(
      final long userTaskKey,
      final long expectedRecords,
      final Consumer<UserTaskRecordValue> consumer) {
    final UserTaskRecordValue userTask =
        RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNED)
            .filter(record -> record.getKey() == userTaskKey)
            .limit(expectedRecords)
            .map(Record::getValue)
            .toList()
            .getLast();

    assertThat(userTask).isNotNull();
    consumer.accept(userTask);
  }

  public static void assertUserTaskUpdated(
      final long userTaskKey, final Consumer<UserTaskRecordValue> consumer) {
    assertUserTaskUpdated(userTaskKey, 1, consumer);
  }

  public static void assertUserTaskUpdated(
      final long userTaskKey,
      final long expectedRecords,
      final Consumer<UserTaskRecordValue> consumer) {
    final UserTaskRecordValue userTask =
        RecordingExporter.userTaskRecords(UserTaskIntent.UPDATED)
            .filter(record -> record.getKey() == userTaskKey)
            .limit(expectedRecords)
            .map(Record::getValue)
            .toList()
            .getLast();

    assertThat(userTask).isNotNull();
    consumer.accept(userTask);
  }

  public static void assertUserTaskCanceled(
      final long userTaskKey, final Consumer<UserTaskRecordValue> consumer) {
    final UserTaskRecordValue userTask =
        RecordingExporter.userTaskRecords(UserTaskIntent.CANCELED)
            .withRecordKey(userTaskKey)
            .getFirst()
            .getValue();

    consumer.accept(userTask);
  }

  public static void assertClockPinned(final Consumer<ClockRecordValue> consumer) {
    assertClockRecordValue(ClockIntent.PINNED, consumer);
  }

  public static void assertClockResetted(final Consumer<ClockRecordValue> consumer) {
    assertClockRecordValue(ClockIntent.RESETTED, consumer);
  }

  private static void assertClockRecordValue(
      final ClockIntent intent, final Consumer<ClockRecordValue> consumer) {
    final ClockRecordValue clockRecord =
        RecordingExporter.clockRecords(intent).findFirst().map(Record::getValue).orElse(null);

    assertThat(clockRecord).isNotNull();
    consumer.accept(clockRecord);
  }

  public static void assertElementCompleted(final String bpmnId, final String activity) {
    assertElementCompleted(bpmnId, activity, (e) -> {});
  }

  public static void assertElementCompleted(
      final String bpmnId,
      final String activity,
      final Consumer<ProcessInstanceRecordValue> eventConsumer) {
    final Record<ProcessInstanceRecordValue> processInstanceRecordValueRecord =
        RecordingExporter.processInstanceRecords(ELEMENT_COMPLETED)
            .withBpmnProcessId(bpmnId)
            .withElementId(activity)
            .findFirst()
            .orElse(null);

    assertThat(processInstanceRecordValueRecord).isNotNull();

    eventConsumer.accept(processInstanceRecordValueRecord.getValue());
  }

  public static void assertElementCompleted(
      final long processInstanceKey,
      final String activity,
      final Consumer<ProcessInstanceRecordValue> eventConsumer) {
    final Record<ProcessInstanceRecordValue> processInstanceRecordValueRecord =
        RecordingExporter.processInstanceRecords(ELEMENT_COMPLETED)
            .withElementId(activity)
            .withProcessInstanceKey(processInstanceKey)
            .findFirst()
            .orElse(null);

    assertThat(processInstanceRecordValueRecord).isNotNull();

    eventConsumer.accept(processInstanceRecordValueRecord.getValue());
  }

  public static void assertProcessInstanceState(
      final long processInstanceKey,
      final ProcessInstanceIntent intent,
      final Consumer<ProcessInstanceRecordValue> consumer) {
    consumeFirstProcessInstanceRecord(
        RecordingExporter.processInstanceRecords(intent)
            .withProcessInstanceKey(processInstanceKey)
            .filter(r -> r.getKey() == r.getValue().getProcessInstanceKey()),
        consumer);
  }

  public static void assertProcessInstanceCreated(
      final long processInstanceKey, final Consumer<ProcessInstanceRecordValue> consumer) {
    assertProcessInstanceState(
        processInstanceKey, ProcessInstanceIntent.ELEMENT_ACTIVATING, consumer);
  }

  public static void assertProcessInstanceState(
      final ProcessInstanceIntent intent, final Consumer<ProcessInstanceRecordValue> consumer) {
    consumeFirstProcessInstanceRecord(
        RecordingExporter.processInstanceRecords(intent)
            .filter(r -> r.getKey() == r.getValue().getProcessInstanceKey()),
        consumer);
  }

  public static void assertElementInState(
      final long processInstanceKey, final String elementId, final ProcessInstanceIntent intent) {
    consumeFirstProcessInstanceRecord(
        RecordingExporter.processInstanceRecords(intent)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(elementId),
        v -> {});
  }

  public static void assertElementInState(
      final long processInstanceKey,
      final String elementId,
      final BpmnElementType elementType,
      final ProcessInstanceIntent intent) {
    consumeFirstProcessInstanceRecord(
        RecordingExporter.processInstanceRecords(intent)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(elementType)
            .withElementId(elementId),
        v -> {});
  }

  public static void assertElementInState(
      final ProcessInstanceIntent intent,
      final String element,
      final Consumer<ProcessInstanceRecordValue> consumer) {
    consumeFirstProcessInstanceRecord(
        RecordingExporter.processInstanceRecords(intent).withElementId(element), consumer);
  }

  public static void assertElementRecordInState(
      final long processInstanceKey,
      final String element,
      final ProcessInstanceIntent intent,
      final Consumer<Record<ProcessInstanceRecordValue>> consumer) {
    final Record<ProcessInstanceRecordValue> record =
        RecordingExporter.processInstanceRecords(intent)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(element)
            .findFirst()
            .orElse(null);

    assertThat(record).isNotNull();
    consumer.accept(record);
  }

  private static void consumeFirstProcessInstanceRecord(
      final ProcessInstanceRecordStream stream,
      final Consumer<ProcessInstanceRecordValue> consumer) {

    final ProcessInstanceRecordValue value = stream.findFirst().map(Record::getValue).orElse(null);

    assertThat(value).isNotNull();

    consumer.accept(value);
  }

  public static void assertIncidentResolved() {
    assertThat(RecordingExporter.incidentRecords(IncidentIntent.RESOLVED).exists()).isTrue();
  }

  public static void assertIncidentResolveFailed() {
    assertThat(RecordingExporter.incidentRecords(IncidentIntent.RESOLVED).exists()).isTrue();

    assertThat(
            RecordingExporter.incidentRecords()
                .skipUntil(e -> e.getIntent() == IncidentIntent.RESOLVED)
                .filter(e -> e.getIntent() == IncidentIntent.CREATED)
                .exists())
        .isTrue();
  }

  public static void assertVariableDocumentUpdated() {
    assertVariableDocumentUpdated(e -> {});
  }

  public static void assertVariableDocumentUpdated(
      final Consumer<VariableDocumentRecordValue> eventConsumer) {
    final Record<VariableDocumentRecordValue> record =
        RecordingExporter.variableDocumentRecords(VariableDocumentIntent.UPDATED)
            .findFirst()
            .orElse(null);

    assertThat(record).isNotNull();
    eventConsumer.accept(record.getValue());
  }

  public static void assertRoleCreated(
      final String roleName, final Consumer<RoleRecordValue> consumer) {
    final RoleRecordValue roleRecordValue =
        RecordingExporter.roleRecords()
            .withIntent(RoleIntent.CREATED)
            .withName(roleName)
            .getFirst()
            .getValue();

    assertThat(roleRecordValue).isNotNull();
    consumer.accept(roleRecordValue);
  }

  public static void assertGroupCreated(
      final String groupName, final Consumer<GroupRecordValue> consumer) {
    final GroupRecordValue groupRecordValue =
        RecordingExporter.groupRecords()
            .withIntent(GroupIntent.CREATED)
            .withName(groupName)
            .getFirst()
            .getValue();

    assertThat(groupRecordValue).isNotNull();
    consumer.accept(groupRecordValue);
  }

  public static void assertGroupUpdated(
      final String groupName, final Consumer<GroupRecordValue> consumer) {
    final GroupRecordValue groupRecordValue =
        RecordingExporter.groupRecords()
            .withIntent(GroupIntent.UPDATED)
            .withName(groupName)
            .getFirst()
            .getValue();

    assertThat(groupRecordValue).isNotNull();
    consumer.accept(groupRecordValue);
  }

  public static void assertGroupDeleted(
      final String groupId, final Consumer<GroupRecordValue> consumer) {
    final GroupRecordValue groupRecordValue =
        RecordingExporter.groupRecords()
            .withIntent(GroupIntent.DELETED)
            .withGroupId(groupId)
            .getFirst()
            .getValue();

    assertThat(groupRecordValue).isNotNull();
    consumer.accept(groupRecordValue);
  }

  public static void assertEntityAssignedToGroup(
      final String groupId, final String entityId, final Consumer<GroupRecordValue> consumer) {
    final GroupRecordValue groupRecordValue =
        RecordingExporter.groupRecords()
            .withIntent(GroupIntent.ENTITY_ADDED)
            .withGroupId(groupId)
            .withEntityId(entityId)
            .getFirst()
            .getValue();

    assertThat(groupRecordValue).isNotNull();
    consumer.accept(groupRecordValue);
  }

  public static void assertEntityUnassignedFromGroup(
      final String groupId, final String entityId, final Consumer<GroupRecordValue> consumer) {
    final GroupRecordValue groupRecordValue =
        RecordingExporter.groupRecords()
            .withIntent(GroupIntent.ENTITY_REMOVED)
            .withGroupId(groupId)
            .withEntityId(entityId)
            .getFirst()
            .getValue();

    assertThat(groupRecordValue).isNotNull();
    consumer.accept(groupRecordValue);
  }

  public static void assertUserCreated(final String username) {
    assertUserCreated(username, u -> {});
  }

  public static void assertUserCreated(
      final String username, final Consumer<UserRecordValue> consumer) {
    final UserRecordValue user =
        RecordingExporter.userRecords()
            .withIntent(UserIntent.CREATED)
            .withUsername(username)
            .getFirst()
            .getValue();

    assertThat(user).isNotNull();
    consumer.accept(user);
  }

  public static void assertTenantCreated(
      final String tenantId, final Consumer<TenantRecordValue> consumer) {
    final TenantRecordValue tenantRecordValue =
        RecordingExporter.tenantRecords()
            .withIntent(TenantIntent.CREATED)
            .withTenantId(tenantId)
            .getFirst()
            .getValue();

    assertThat(tenantRecordValue).isNotNull();
    consumer.accept(tenantRecordValue);
  }

  public static void assertTenantUpdated(
      final String tenantId, final Consumer<TenantRecordValue> consumer) {
    final TenantRecordValue tenantRecordValue =
        RecordingExporter.tenantRecords()
            .withIntent(TenantIntent.UPDATED)
            .withTenantId(tenantId)
            .getFirst()
            .getValue();

    assertThat(tenantRecordValue).isNotNull();
    consumer.accept(tenantRecordValue);
  }

  public static void assertTenantDeleted(final String tenantId) {
    final TenantRecordValue tenantRecordValue =
        RecordingExporter.tenantRecords()
            .withIntent(TenantIntent.DELETED)
            .withTenantId(tenantId)
            .getFirst()
            .getValue();

    assertThat(tenantRecordValue).isNotNull();
  }

  public static void assertEntityAssignedToTenant(
      final String tenantId, final String entityId, final Consumer<TenantRecordValue> consumer) {
    final TenantRecordValue tenantRecordValue =
        RecordingExporter.tenantRecords()
            .withIntent(TenantIntent.ENTITY_ADDED)
            .withTenantId(tenantId)
            .withEntityId(entityId)
            .getFirst()
            .getValue();

    assertThat(tenantRecordValue).isNotNull();
    consumer.accept(tenantRecordValue);
  }

  public static void assertEntityRemovedFromTenant(
      final String tenantId, final String entityId, final Consumer<TenantRecordValue> consumer) {
    final TenantRecordValue tenantRecordValue =
        RecordingExporter.tenantRecords()
            .withIntent(TenantIntent.ENTITY_REMOVED)
            .withTenantId(tenantId)
            .withEntityId(entityId)
            .getFirst()
            .getValue();

    assertThat(tenantRecordValue).isNotNull();
    consumer.accept(tenantRecordValue);
  }

  public static void assertMappingRuleCreated(
      final String mappingRuleId,
      final String claimName,
      final String claimValue,
      final Consumer<MappingRuleRecordValue> consumer) {
    final MappingRuleRecordValue mappingRule =
        RecordingExporter.mappingRuleRecords()
            .withIntent(MappingRuleIntent.CREATED)
            .withMappingRuleId(mappingRuleId)
            .withClaimName(claimName)
            .withClaimValue(claimValue)
            .getFirst()
            .getValue();

    assertThat(mappingRule).isNotNull();
    consumer.accept(mappingRule);
  }

  public static void assertGroupUnassignedFromTenant(
      final String tenantId, final Consumer<TenantRecordValue> consumer) {
    final var tenantRecordValue =
        RecordingExporter.tenantRecords()
            .withIntent(TenantIntent.ENTITY_REMOVED)
            .withTenantId(tenantId)
            .getFirst()
            .getValue();

    assertThat(tenantRecordValue).isNotNull();
    consumer.accept(tenantRecordValue);
  }
}
