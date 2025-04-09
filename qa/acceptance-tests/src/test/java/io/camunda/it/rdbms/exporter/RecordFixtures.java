/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.exporter;

import io.camunda.zeebe.protocol.record.ImmutableRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.intent.BatchOperationChunkIntent;
import io.camunda.zeebe.protocol.record.intent.BatchOperationExecutionIntent;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.intent.DecisionIntent;
import io.camunda.zeebe.protocol.record.intent.DecisionRequirementsIntent;
import io.camunda.zeebe.protocol.record.intent.FormIntent;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.MappingIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationRecordValue;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.GroupRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableAuthorizationRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableBatchOperationChunkRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableBatchOperationExecutionRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableBatchOperationLifecycleManagementRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableGroupRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableIncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableRoleRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableTenantRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableUserRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableUserTaskRecordValue;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.RoleRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantRecordValue;
import io.camunda.zeebe.protocol.record.value.UserRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRequirementsRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.ImmutableDecisionRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.ImmutableDecisionRequirementsRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.ImmutableProcess;
import io.camunda.zeebe.protocol.record.value.deployment.Process;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

public class RecordFixtures {

  protected static final ProtocolFactory FACTORY = new ProtocolFactory(System.nanoTime());

  protected static ImmutableRecord<RecordValue> getProcessInstanceStartedRecord(
      final Long position) {
    final io.camunda.zeebe.protocol.record.Record<RecordValue> recordValueRecord =
        FACTORY.generateRecord(ValueType.PROCESS_INSTANCE);
    return ImmutableRecord.builder()
        .from(recordValueRecord)
        .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
        .withPosition(position)
        .withTimestamp(System.currentTimeMillis())
        .withValue(
            ImmutableProcessInstanceRecordValue.builder()
                .from((ProcessInstanceRecordValue) recordValueRecord.getValue())
                .withBpmnElementType(BpmnElementType.PROCESS)
                .withVersion(1)
                .build())
        .build();
  }

  protected static ImmutableRecord<RecordValue> getProcessInstanceCompletedRecord(
      final Long position, final long processInstanceKey) {
    final io.camunda.zeebe.protocol.record.Record<RecordValue> recordValueRecord =
        FACTORY.generateRecord(ValueType.PROCESS_INSTANCE);
    return ImmutableRecord.builder()
        .from(recordValueRecord)
        .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withPosition(position)
        .withTimestamp(System.currentTimeMillis())
        .withKey(processInstanceKey)
        .withValue(
            ImmutableProcessInstanceRecordValue.builder()
                .from((ProcessInstanceRecordValue) recordValueRecord.getValue())
                .withProcessInstanceKey(processInstanceKey)
                .withBpmnElementType(BpmnElementType.PROCESS)
                .withVersion(1)
                .build())
        .build();
  }

  protected static ImmutableRecord<RecordValue> getProcessDefinitionCreatedRecord(
      final Long position) {
    final io.camunda.zeebe.protocol.record.Record<RecordValue> recordValueRecord =
        FACTORY.generateRecord(ValueType.PROCESS);

    final byte[] resource;
    try {
      final var resourceUrl =
          RecordFixtures.class.getClassLoader().getResource("process/process_start_form.bpmn");
      assert resourceUrl != null;
      resource = Files.readAllBytes(Path.of(resourceUrl.getPath()));
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }

    return ImmutableRecord.builder()
        .from(recordValueRecord)
        .withIntent(ProcessIntent.CREATED)
        .withPosition(position)
        .withTimestamp(System.currentTimeMillis())
        .withPartitionId(1)
        .withValue(
            ImmutableProcess.builder()
                .from((Process) recordValueRecord.getValue())
                .withResource(resource)
                .withBpmnProcessId("Process_11hxie4")
                .withVersion(1)
                .build())
        .build();
  }

  protected static ImmutableRecord<RecordValue> getDecisionRequirementsCreatedRecord(
      final Long position) {
    final io.camunda.zeebe.protocol.record.Record<RecordValue> recordValueRecord =
        FACTORY.generateRecord(ValueType.DECISION_REQUIREMENTS);

    return ImmutableRecord.builder()
        .from(recordValueRecord)
        .withIntent(DecisionRequirementsIntent.CREATED)
        .withPosition(position)
        .withTimestamp(System.currentTimeMillis())
        .withPartitionId(1)
        .withValue(
            ImmutableDecisionRequirementsRecordValue.builder()
                .from((DecisionRequirementsRecordValue) recordValueRecord.getValue())
                .withDecisionRequirementsVersion(1)
                .build())
        .build();
  }

  protected static ImmutableRecord<RecordValue> getDecisionDefinitionCreatedRecord(
      final Long position) {
    final io.camunda.zeebe.protocol.record.Record<RecordValue> recordValueRecord =
        FACTORY.generateRecord(ValueType.DECISION);

    return ImmutableRecord.builder()
        .from(recordValueRecord)
        .withIntent(DecisionIntent.CREATED)
        .withPosition(position)
        .withTimestamp(System.currentTimeMillis())
        .withPartitionId(1)
        .withValue(
            ImmutableDecisionRecordValue.builder()
                .from((ImmutableDecisionRecordValue) recordValueRecord.getValue())
                .withVersion(1)
                .build())
        .build();
  }

  protected static ImmutableRecord<RecordValue> getFlowNodeActivatingRecord(final Long position) {
    final io.camunda.zeebe.protocol.record.Record<RecordValue> recordValueRecord =
        FACTORY.generateRecord(ValueType.PROCESS_INSTANCE);

    return getFlowNodeActivatingRecord(position, FACTORY.generateObject(Long.class));
  }

  protected static ImmutableRecord<RecordValue> getFlowNodeActivatingRecord(
      final Long position, final long processInstanceKey) {
    final io.camunda.zeebe.protocol.record.Record<RecordValue> recordValueRecord =
        FACTORY.generateRecord(ValueType.PROCESS_INSTANCE);

    return ImmutableRecord.builder()
        .from(recordValueRecord)
        .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
        .withPosition(position)
        .withTimestamp(System.currentTimeMillis())
        .withPartitionId(1)
        .withValue(
            ImmutableProcessInstanceRecordValue.builder()
                .from((ProcessInstanceRecordValue) recordValueRecord.getValue())
                .withProcessInstanceKey(processInstanceKey)
                .withBpmnElementType(BpmnElementType.SERVICE_TASK)
                .withVersion(1)
                .withElementInstancePath(List.of(List.of(1L, 2L)))
                .build())
        .build();
  }

  protected static ImmutableRecord<RecordValue> getFlowNodeCompletedRecord(
      final Long position, final long elementKey) {
    final io.camunda.zeebe.protocol.record.Record<RecordValue> recordValueRecord =
        FACTORY.generateRecord(ValueType.PROCESS_INSTANCE);

    return ImmutableRecord.builder()
        .from(recordValueRecord)
        .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withPosition(position)
        .withTimestamp(System.currentTimeMillis())
        .withPartitionId(1)
        .withKey(elementKey)
        .withValue(
            ImmutableProcessInstanceRecordValue.builder()
                .from((ProcessInstanceRecordValue) recordValueRecord.getValue())
                .withProcessInstanceKey(elementKey)
                .withBpmnElementType(BpmnElementType.SERVICE_TASK)
                .withVersion(1)
                .build())
        .build();
  }

  protected static ImmutableRecord<RecordValue> getUserTaskCreatedRecord(final Long position) {
    final Record<RecordValue> recordValueRecord = FACTORY.generateRecord(ValueType.USER_TASK);

    return ImmutableRecord.builder()
        .from(recordValueRecord)
        .withIntent(UserTaskIntent.CREATED)
        .withPosition(position)
        .withTimestamp(System.currentTimeMillis())
        .withPartitionId(1)
        .withValue(
            ImmutableUserTaskRecordValue.builder()
                .from((ImmutableUserTaskRecordValue) recordValueRecord.getValue())
                .withCreationTimestamp(OffsetDateTime.now().toEpochSecond())
                .withDueDate(OffsetDateTime.now().toString())
                .withFollowUpDate(OffsetDateTime.now().toString())
                .withProcessDefinitionVersion(1)
                .withCandidateUsersList(List.of("user1", "user2"))
                .withCandidateGroupsList(List.of("group1", "group2"))
                .build())
        .build();
  }

  protected static ImmutableRecord<RecordValue> getFormCreatedRecord(final Long position) {
    final Record<RecordValue> recordValueRecord = FACTORY.generateRecord(ValueType.FORM);

    return ImmutableRecord.builder()
        .from(recordValueRecord)
        .withIntent(FormIntent.CREATED)
        .withPosition(position)
        .withTimestamp(System.currentTimeMillis())
        .withPartitionId(1)
        .build();
  }

  protected static ImmutableRecord<RecordValue> getTenantRecord(
      final Long tenantKey, final String tenantId, final TenantIntent intent) {
    final Record<RecordValue> recordValueRecord = FACTORY.generateRecord(ValueType.TENANT);
    return ImmutableRecord.builder()
        .from(recordValueRecord)
        .withIntent(intent)
        .withPosition(1)
        .withTimestamp(System.currentTimeMillis())
        .withKey(tenantKey)
        .withValue(
            ImmutableTenantRecordValue.builder()
                .from((TenantRecordValue) recordValueRecord.getValue())
                .withTenantId(tenantId)
                .withTenantKey(tenantKey)
                .withEntityType(EntityType.USER)
                .build())
        .build();
  }

  protected static ImmutableRecord<RecordValue> getUserRecord(
      final Long userKey, final String username, final UserIntent intent) {
    final Record<RecordValue> recordValueRecord = FACTORY.generateRecord(ValueType.USER);

    return ImmutableRecord.builder()
        .from(recordValueRecord)
        .withIntent(intent)
        .withPosition(1)
        .withTimestamp(System.currentTimeMillis())
        .withKey(userKey)
        .withValue(
            ImmutableUserRecordValue.builder()
                .from((UserRecordValue) recordValueRecord.getValue())
                .withUserKey(userKey)
                .withUsername(username)
                .build())
        .build();
  }

  protected static ImmutableRecord<RecordValue> getMappingRecord(
      final Long position, final MappingIntent intent) {
    final Record<RecordValue> recordValueRecord = FACTORY.generateRecord(ValueType.MAPPING);

    return ImmutableRecord.builder()
        .from(recordValueRecord)
        .withIntent(intent)
        .withPosition(position)
        .withTimestamp(System.currentTimeMillis())
        .withPartitionId(1)
        .build();
  }

  protected static ImmutableRecord<RecordValue> getRoleRecord(
      final Long roleKey, final RoleIntent intent) {
    return getRoleRecord(roleKey, intent, null);
  }

  protected static ImmutableRecord<RecordValue> getRoleRecord(
      final Long roleKey, final RoleIntent intent, final Long entityKey) {
    final Record<RecordValue> recordValueRecord = FACTORY.generateRecord(ValueType.ROLE);
    return ImmutableRecord.builder()
        .from(recordValueRecord)
        .withIntent(intent)
        .withPosition(1)
        .withTimestamp(System.currentTimeMillis())
        .withKey(roleKey)
        .withValue(
            ImmutableRoleRecordValue.builder()
                .from((RoleRecordValue) recordValueRecord.getValue())
                .withRoleKey(roleKey)
                .withEntityKey(entityKey != null ? entityKey : 0)
                .withEntityType(entityKey != null ? EntityType.USER : null)
                .build())
        .build();
  }

  protected static ImmutableRecord<RecordValue> getGroupRecord(
      final Long groupKey, final GroupIntent intent) {
    return getGroupRecord(groupKey, intent, null);
  }

  protected static ImmutableRecord<RecordValue> getGroupRecord(
      final Long groupKey, final GroupIntent intent, final Long entityKey) {
    final Record<RecordValue> recordValueRecord = FACTORY.generateRecord(ValueType.GROUP);
    return ImmutableRecord.builder()
        .from(recordValueRecord)
        .withIntent(intent)
        .withPosition(1)
        .withTimestamp(System.currentTimeMillis())
        .withKey(groupKey)
        .withValue(
            ImmutableGroupRecordValue.builder()
                .from((GroupRecordValue) recordValueRecord.getValue())
                .withGroupKey(groupKey)
                // TODO: revisit with https://github.com/camunda/camunda/issues/29903
                .withEntityId(entityKey != null ? String.valueOf(entityKey) : "0")
                .withEntityType(entityKey != null ? EntityType.USER : null)
                .build())
        .build();
  }

  protected static ImmutableRecord<RecordValue> getIncidentRecord(
      final IncidentIntent intent,
      final long incidentKey,
      final long processInstanceKey,
      final long flowNodeInstanceKey) {
    final Record<RecordValue> recordValueRecord = FACTORY.generateRecord(ValueType.INCIDENT);
    return ImmutableRecord.builder()
        .from(recordValueRecord)
        .withKey(incidentKey)
        .withIntent(intent)
        .withPosition(1L)
        .withTimestamp(System.currentTimeMillis())
        .withValue(
            ImmutableIncidentRecordValue.builder()
                .from((IncidentRecordValue) recordValueRecord.getValue())
                .withElementInstanceKey(flowNodeInstanceKey)
                .withProcessInstanceKey(processInstanceKey)
                .withElementInstancePath(List.of(List.of(processInstanceKey, flowNodeInstanceKey)))
                .build())
        .build();
  }

  protected static ImmutableRecord<RecordValue> getAuthorizationRecord(
      final AuthorizationIntent intent,
      final Long authorizationKey,
      final String ownerId,
      final AuthorizationOwnerType ownerType,
      final AuthorizationResourceType resourceType,
      final String resourceId,
      final Set<PermissionType> permissionTypes) {
    final Record<RecordValue> recordValueRecord = FACTORY.generateRecord(ValueType.AUTHORIZATION);
    return ImmutableRecord.builder()
        .from(recordValueRecord)
        .withIntent(intent)
        .withPosition(1)
        .withTimestamp(System.currentTimeMillis())
        .withValue(
            ImmutableAuthorizationRecordValue.builder()
                .from((AuthorizationRecordValue) recordValueRecord.getValue())
                .withAuthorizationKey(authorizationKey)
                .withOwnerId(ownerId)
                .withOwnerType(ownerType)
                .withResourceType(resourceType)
                .withResourceId(resourceId)
                .withPermissionTypes(permissionTypes)
                .build())
        .build();
  }

  protected static ImmutableRecord<RecordValue> getBatchOperationCreatedRecord(
      final Long position) {
    final Record<RecordValue> recordValueRecord =
        FACTORY.generateRecord(ValueType.BATCH_OPERATION_CREATION);

    return ImmutableRecord.builder()
        .from(recordValueRecord)
        .withIntent(BatchOperationIntent.CREATED)
        .withPosition(position)
        .withTimestamp(System.currentTimeMillis())
        .build();
  }

  protected static ImmutableRecord<RecordValue> getBatchOperationChunkRecord(
      final Long batchOperationKey, final Long position) {
    final Record<RecordValue> recordValueRecord =
        FACTORY.generateRecord(ValueType.BATCH_OPERATION_CHUNK);

    return ImmutableRecord.builder()
        .from(recordValueRecord)
        .withIntent(BatchOperationChunkIntent.CREATE)
        .withPosition(position)
        .withTimestamp(System.currentTimeMillis())
        .withValue(
            ImmutableBatchOperationChunkRecordValue.builder()
                .from((ImmutableBatchOperationChunkRecordValue) recordValueRecord.getValue())
                .withBatchOperationKey(batchOperationKey)
                .withItemKeys(List.of(1L, 2L, 3L))
                .build())
        .build();
  }

  protected static ImmutableRecord<RecordValue> getBatchOperationExecutionCompletedRecord(
      final Long batchOperationKey, final Long position) {
    final Record<RecordValue> recordValueRecord =
        FACTORY.generateRecord(ValueType.BATCH_OPERATION_EXECUTION);

    return ImmutableRecord.builder()
        .from(recordValueRecord)
        .withIntent(BatchOperationExecutionIntent.COMPLETED)
        .withPosition(position)
        .withTimestamp(System.currentTimeMillis())
        .withValue(
            ImmutableBatchOperationExecutionRecordValue.builder()
                .from((ImmutableBatchOperationExecutionRecordValue) recordValueRecord.getValue())
                .withBatchOperationKey(batchOperationKey)
                .build())
        .build();
  }

  protected static ImmutableRecord<RecordValue> getBatchOperationLifecycleCanceledRecord(
      final Long batchOperationKey, final Long position) {
    final Record<RecordValue> recordValueRecord =
        FACTORY.generateRecord(ValueType.BATCH_OPERATION_LIFECYCLE_MANAGEMENT);

    return ImmutableRecord.builder()
        .from(recordValueRecord)
        .withIntent(BatchOperationIntent.CANCELED)
        .withPosition(position)
        .withTimestamp(System.currentTimeMillis())
        .withValue(
            ImmutableBatchOperationLifecycleManagementRecordValue.builder()
                .from(
                    (ImmutableBatchOperationLifecycleManagementRecordValue)
                        recordValueRecord.getValue())
                .withBatchOperationKey(batchOperationKey)
                .build())
        .build();
  }

  protected static ImmutableRecord<RecordValue> getBatchOperationProcessCancelledRecord(
      final Long processInstanceKey, final Long batchOperationKey, final Long position) {
    final Record<RecordValue> recordValueRecord =
        FACTORY.generateRecord(ValueType.PROCESS_INSTANCE);

    return ImmutableRecord.builder()
        .from(recordValueRecord)
        .withIntent(ProcessInstanceIntent.ELEMENT_TERMINATED)
        .withKey(processInstanceKey)
        .withPosition(position)
        .withTimestamp(System.currentTimeMillis())
        .withOperationReference(batchOperationKey)
        .withValue(
            ImmutableProcessInstanceRecordValue.builder()
                .from((ImmutableProcessInstanceRecordValue) recordValueRecord.getValue())
                .withBpmnElementType(BpmnElementType.PROCESS)
                .withParentProcessInstanceKey(-1)
                .build())
        .build();
  }

  protected static ImmutableRecord<RecordValue> getFailedBatchOperationProcessCancelledRecord(
      final Long processInstanceKey, final Long batchOperationKey, final Long position) {
    final Record<RecordValue> recordValueRecord =
        FACTORY.generateRecord(ValueType.PROCESS_INSTANCE);

    return ImmutableRecord.builder()
        .from(recordValueRecord)
        .withIntent(ProcessInstanceIntent.CANCEL)
        .withRejectionType(RejectionType.INVALID_STATE)
        .withKey(processInstanceKey)
        .withPosition(position)
        .withTimestamp(System.currentTimeMillis())
        .withOperationReference(batchOperationKey)
        .withValue(
            ImmutableProcessInstanceRecordValue.builder()
                .from((ImmutableProcessInstanceRecordValue) recordValueRecord.getValue())
                .withBpmnElementType(BpmnElementType.PROCESS)
                .withParentProcessInstanceKey(-1)
                .build())
        .build();
  }
}
