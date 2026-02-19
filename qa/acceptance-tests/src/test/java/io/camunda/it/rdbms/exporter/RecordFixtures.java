/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.exporter;

import static java.util.Collections.emptyList;
import static org.jeasy.random.FieldPredicates.inClass;
import static org.jeasy.random.FieldPredicates.named;

import io.camunda.zeebe.protocol.record.ImmutableRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.intent.BatchOperationChunkIntent;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.intent.ClusterVariableIntent;
import io.camunda.zeebe.protocol.record.intent.DecisionEvaluationIntent;
import io.camunda.zeebe.protocol.record.intent.DecisionIntent;
import io.camunda.zeebe.protocol.record.intent.DecisionRequirementsIntent;
import io.camunda.zeebe.protocol.record.intent.FormIntent;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.intent.HistoryDeletionIntent;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobMetricsBatchIntent;
import io.camunda.zeebe.protocol.record.intent.MappingRuleIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceMigrationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationRecordValue;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ClusterVariableRecordValue;
import io.camunda.zeebe.protocol.record.value.ClusterVariableScope;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.EvaluatedDecisionValue;
import io.camunda.zeebe.protocol.record.value.GroupRecordValue;
import io.camunda.zeebe.protocol.record.value.HistoryDeletionType;
import io.camunda.zeebe.protocol.record.value.ImmutableAuthorizationRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableBatchOperationChunkRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableBatchOperationCreationRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableBatchOperationInitializationRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableBatchOperationItemValue;
import io.camunda.zeebe.protocol.record.value.ImmutableBatchOperationLifecycleManagementRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableClusterVariableRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableDecisionEvaluationRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableGroupRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableHistoryDeletionRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableIncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableJobMetricsBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableJobRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessInstanceMigrationRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessInstanceModificationRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableRoleRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableTenantRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableUserRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableUserTaskRecordValue;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.JobMetricsBatchRecordValue.JobMetricsValue;
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
import io.camunda.zeebe.protocol.record.value.scaling.BatchOperationErrorValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public class RecordFixtures {

  protected static final long NO_PARENT_EXISTS_KEY = -1L;

  protected static final ProtocolFactory FACTORY =
      new ProtocolFactory(System.nanoTime())
          // retries field is SMALLINT, so need to limit range
          .registerRandomizer(
              named("retries").and(inClass(ImmutableJobRecordValue.class)),
              random -> random.nextInt(1, 32_000));

  private static final AtomicLong POSITION = new AtomicLong(1L);

  public long nextPosition() {
    return POSITION.incrementAndGet();
  }

  public ImmutableRecord<RecordValue> getProcessInstanceStartedRecord() {
    return getProcessInstanceStartedRecord(NO_PARENT_EXISTS_KEY);
  }

  public ImmutableRecord<RecordValue> getProcessInstanceStartedRecord(
      final long parentProcessInstanceKey) {
    final io.camunda.zeebe.protocol.record.Record<RecordValue> recordValueRecord =
        FACTORY.generateRecord(ValueType.PROCESS_INSTANCE);
    return ImmutableRecord.builder()
        .from(recordValueRecord)
        .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
        .withPosition(nextPosition())
        .withPartitionId(1)
        .withTimestamp(System.currentTimeMillis())
        .withValue(
            ImmutableProcessInstanceRecordValue.builder()
                .from((ProcessInstanceRecordValue) recordValueRecord.getValue())
                .withBpmnElementType(BpmnElementType.PROCESS)
                .withParentProcessInstanceKey(parentProcessInstanceKey)
                .withParentElementInstanceKey(parentProcessInstanceKey)
                .withVersion(1)
                .build())
        .build();
  }

  public ImmutableRecord<RecordValue> getProcessInstanceCompletedRecord(
      final long processInstanceKey) {
    return getProcessInstanceCompletedRecord(processInstanceKey, 42L);
  }

  public ImmutableRecord<RecordValue> getProcessInstanceCompletedRecord(
      final long processInstanceKey, final long parentProcessInstanceKey) {
    final io.camunda.zeebe.protocol.record.Record<RecordValue> recordValueRecord =
        FACTORY.generateRecord(ValueType.PROCESS_INSTANCE);
    return ImmutableRecord.builder()
        .from(recordValueRecord)
        .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withPosition(nextPosition())
        .withPartitionId(1)
        .withTimestamp(System.currentTimeMillis())
        .withKey(processInstanceKey)
        .withValue(
            ImmutableProcessInstanceRecordValue.builder()
                .from((ProcessInstanceRecordValue) recordValueRecord.getValue())
                .withProcessInstanceKey(processInstanceKey)
                .withBpmnElementType(BpmnElementType.PROCESS)
                .withParentProcessInstanceKey(parentProcessInstanceKey)
                .withParentElementInstanceKey(parentProcessInstanceKey)
                .withVersion(1)
                .build())
        .build();
  }

  public ImmutableRecord<RecordValue> getProcessDefinitionCreatedRecord() {
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
        .withPosition(nextPosition())
        .withPartitionId(1)
        .withTimestamp(System.currentTimeMillis())
        .withValue(
            ImmutableProcess.builder()
                .from((Process) recordValueRecord.getValue())
                .withResource(resource)
                .withBpmnProcessId("Process_11hxie4")
                .withVersion(1)
                .build())
        .build();
  }

  public ImmutableRecord<RecordValue> getDecisionRequirementsCreatedRecord() {
    final io.camunda.zeebe.protocol.record.Record<RecordValue> recordValueRecord =
        FACTORY.generateRecord(ValueType.DECISION_REQUIREMENTS);

    return ImmutableRecord.builder()
        .from(recordValueRecord)
        .withIntent(DecisionRequirementsIntent.CREATED)
        .withPosition(nextPosition())
        .withPartitionId(1)
        .withTimestamp(System.currentTimeMillis())
        .withValue(
            ImmutableDecisionRequirementsRecordValue.builder()
                .from((DecisionRequirementsRecordValue) recordValueRecord.getValue())
                .withDecisionRequirementsVersion(1)
                .build())
        .build();
  }

  public ImmutableRecord<RecordValue> getDecisionDefinitionCreatedRecord() {
    final io.camunda.zeebe.protocol.record.Record<RecordValue> recordValueRecord =
        FACTORY.generateRecord(ValueType.DECISION);

    return ImmutableRecord.builder()
        .from(recordValueRecord)
        .withIntent(DecisionIntent.CREATED)
        .withPosition(nextPosition())
        .withPartitionId(1)
        .withTimestamp(System.currentTimeMillis())
        .withValue(
            ImmutableDecisionRecordValue.builder()
                .from((ImmutableDecisionRecordValue) recordValueRecord.getValue())
                .withVersion(1)
                .build())
        .build();
  }

  public ImmutableRecord<RecordValue> getDecisionEvaluationEvaluatedRecord(
      final List<EvaluatedDecisionValue> evaluationDecisions) {
    final ImmutableDecisionEvaluationRecordValue value =
        ImmutableDecisionEvaluationRecordValue.builder()
            .from(FACTORY.generateObject(ImmutableDecisionEvaluationRecordValue.class))
            .withEvaluatedDecisions(evaluationDecisions)
            .build();
    final io.camunda.zeebe.protocol.record.Record<RecordValue> recordValueRecord =
        FACTORY.generateRecord(ValueType.DECISION_EVALUATION);

    return ImmutableRecord.builder()
        .from(recordValueRecord)
        .withIntent(DecisionEvaluationIntent.EVALUATED)
        .withPosition(nextPosition())
        .withPartitionId(1)
        .withTimestamp(System.currentTimeMillis())
        .withValue(value)
        .build();
  }

  public ImmutableRecord<RecordValue> getElementActivatingRecord() {
    final io.camunda.zeebe.protocol.record.Record<RecordValue> recordValueRecord =
        FACTORY.generateRecord(ValueType.PROCESS_INSTANCE);

    return getElementActivatingRecord(FACTORY.generateObject(Long.class));
  }

  public ImmutableRecord<RecordValue> getElementActivatingRecord(final long processInstanceKey) {
    final io.camunda.zeebe.protocol.record.Record<RecordValue> recordValueRecord =
        FACTORY.generateRecord(ValueType.PROCESS_INSTANCE);

    return ImmutableRecord.builder()
        .from(recordValueRecord)
        .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
        .withPosition(nextPosition())
        .withPartitionId(1)
        .withTimestamp(System.currentTimeMillis())
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

  public ImmutableRecord<RecordValue> getElementCompletedRecord(final long elementKey) {
    final io.camunda.zeebe.protocol.record.Record<RecordValue> recordValueRecord =
        FACTORY.generateRecord(ValueType.PROCESS_INSTANCE);

    return ImmutableRecord.builder()
        .from(recordValueRecord)
        .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withPosition(nextPosition())
        .withPartitionId(1)
        .withTimestamp(System.currentTimeMillis())
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

  public ImmutableRecord<RecordValue> getSequenceFlowTakenRecord() {
    return getSequenceFlowTakenRecord(FACTORY.generateObject(Long.class));
  }

  public ImmutableRecord<RecordValue> getSequenceFlowTakenRecord(final long elementKey) {
    final io.camunda.zeebe.protocol.record.Record<RecordValue> recordValueRecord =
        FACTORY.generateRecord(ValueType.PROCESS_INSTANCE);

    return ImmutableRecord.builder()
        .from(recordValueRecord)
        .withIntent(ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN)
        .withPosition(nextPosition())
        .withPartitionId(1)
        .withTimestamp(System.currentTimeMillis())
        .withKey(elementKey)
        .withValue(
            ImmutableProcessInstanceRecordValue.builder()
                .from((ProcessInstanceRecordValue) recordValueRecord.getValue())
                .withProcessInstanceKey(elementKey)
                .build())
        .build();
  }

  public ImmutableRecord<RecordValue> getSequenceFlowDeletedRecord(
      final long elementKey, final RecordValue recordValue) {
    final io.camunda.zeebe.protocol.record.Record<RecordValue> recordValueRecord =
        FACTORY.generateRecord(ValueType.PROCESS_INSTANCE);

    return ImmutableRecord.builder()
        .from(recordValueRecord)
        .withIntent(ProcessInstanceIntent.SEQUENCE_FLOW_DELETED)
        .withPosition(nextPosition())
        .withPartitionId(1)
        .withTimestamp(System.currentTimeMillis())
        .withKey(elementKey)
        .withValue(recordValue)
        .build();
  }

  public ImmutableRecord<RecordValue> getUserTaskCreatingRecord() {
    final Record<RecordValue> recordValueRecord = FACTORY.generateRecord(ValueType.USER_TASK);

    return ImmutableRecord.builder()
        .from(recordValueRecord)
        .withIntent(UserTaskIntent.CREATING)
        .withPosition(nextPosition())
        .withPartitionId(1)
        .withTimestamp(System.currentTimeMillis())
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

  public ImmutableRecord<RecordValue> getFormCreatedRecord() {
    final Record<RecordValue> recordValueRecord = FACTORY.generateRecord(ValueType.FORM);

    return ImmutableRecord.builder()
        .from(recordValueRecord)
        .withIntent(FormIntent.CREATED)
        .withPosition(nextPosition())
        .withPartitionId(1)
        .withTimestamp(System.currentTimeMillis())
        .build();
  }

  public ImmutableRecord<RecordValue> getTenantRecord(
      final Long tenantKey, final String tenantId, final TenantIntent intent) {
    final Record<RecordValue> recordValueRecord = FACTORY.generateRecord(ValueType.TENANT);
    return ImmutableRecord.builder()
        .from(recordValueRecord)
        .withIntent(intent)
        .withPosition(nextPosition())
        .withPartitionId(1)
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

  public ImmutableRecord<RecordValue> getUserRecord(
      final Long userKey, final String username, final UserIntent intent) {
    final Record<RecordValue> recordValueRecord = FACTORY.generateRecord(ValueType.USER);

    return ImmutableRecord.builder()
        .from(recordValueRecord)
        .withIntent(intent)
        .withPosition(nextPosition())
        .withPartitionId(1)
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

  public ImmutableRecord<RecordValue> getJobMetricsBatchRecord(
      final JobMetricsBatchIntent intent,
      final long startTime,
      final long endTime,
      final List<String> encodedStrings,
      final List<? extends JobMetricsValue> metrics,
      final boolean recordSizeLimitExceeded) {
    final Record<RecordValue> recordValueRecord =
        FACTORY.generateRecord(ValueType.JOB_METRICS_BATCH);

    return ImmutableRecord.builder()
        .from(recordValueRecord)
        .withIntent(intent)
        .withKey(nextPosition())
        .withPosition(nextPosition())
        .withPartitionId(1)
        .withTimestamp(System.currentTimeMillis())
        .withValue(
            ImmutableJobMetricsBatchRecordValue.builder()
                .withBatchStartTime(startTime)
                .withBatchEndTime(endTime)
                .withJobMetrics(metrics)
                .withEncodedStrings(encodedStrings)
                .withRecordSizeLimitExceeded(recordSizeLimitExceeded)
                .build())
        .build();
  }

  public ImmutableRecord<RecordValue> getGlobalClusterVariableRecord(
      final ClusterVariableIntent intent) {
    final Record<RecordValue> recordValueRecord =
        FACTORY.generateRecord(ValueType.CLUSTER_VARIABLE);

    return ImmutableRecord.builder()
        .from(recordValueRecord)
        .withIntent(intent)
        .withPosition(nextPosition())
        .withPartitionId(1)
        .withTimestamp(System.currentTimeMillis())
        .withValue(
            ImmutableClusterVariableRecordValue.builder()
                .from((ClusterVariableRecordValue) recordValueRecord.getValue())
                .withScope(ClusterVariableScope.GLOBAL)
                .build())
        .build();
  }

  public ImmutableRecord<RecordValue> getTenantClusterVariableRecord(
      final String tenant, final ClusterVariableIntent intent) {
    final Record<RecordValue> recordValueRecord =
        FACTORY.generateRecord(ValueType.CLUSTER_VARIABLE);

    return ImmutableRecord.builder()
        .from(recordValueRecord)
        .withIntent(intent)
        .withPosition(nextPosition())
        .withPartitionId(1)
        .withTimestamp(System.currentTimeMillis())
        .withValue(
            ImmutableClusterVariableRecordValue.builder()
                .from((ClusterVariableRecordValue) recordValueRecord.getValue())
                .withScope(ClusterVariableScope.TENANT)
                .withTenantId(tenant)
                .build())
        .build();
  }

  public ImmutableRecord<RecordValue> getMappingRuleRecord(final MappingRuleIntent intent) {
    final Record<RecordValue> recordValueRecord = FACTORY.generateRecord(ValueType.MAPPING_RULE);

    return ImmutableRecord.builder()
        .from(recordValueRecord)
        .withIntent(intent)
        .withPosition(nextPosition())
        .withPartitionId(1)
        .withTimestamp(System.currentTimeMillis())
        .build();
  }

  public ImmutableRecord<RecordValue> getRoleRecord(final String roleId, final RoleIntent intent) {
    return getRoleRecord(roleId, intent, null);
  }

  public ImmutableRecord<RecordValue> getRoleRecord(
      final String roleId, final RoleIntent intent, final String entityId) {
    final Record<RecordValue> recordValueRecord = FACTORY.generateRecord(ValueType.ROLE);
    final long roleKey = 1L;
    return ImmutableRecord.builder()
        .from(recordValueRecord)
        .withIntent(intent)
        .withPosition(nextPosition())
        .withPartitionId(1)
        .withTimestamp(System.currentTimeMillis())
        .withKey(roleKey)
        .withValue(
            ImmutableRoleRecordValue.builder()
                .from((RoleRecordValue) recordValueRecord.getValue())
                .withRoleKey(roleKey)
                .withRoleId(roleId)
                .withEntityId(entityId)
                .withEntityType(entityId != null ? EntityType.USER : null)
                .build())
        .build();
  }

  public ImmutableRecord<RecordValue> getGroupRecord(
      final String groupId, final GroupIntent intent) {
    return getGroupRecord(groupId, intent, null);
  }

  public ImmutableRecord<RecordValue> getGroupRecord(
      final String groupId, final GroupIntent intent, final String entityId) {
    final Record<RecordValue> recordValueRecord = FACTORY.generateRecord(ValueType.GROUP);
    final long groupKey = 1L;
    return ImmutableRecord.builder()
        .from(recordValueRecord)
        .withIntent(intent)
        .withPosition(nextPosition())
        .withPartitionId(1)
        .withTimestamp(System.currentTimeMillis())
        .withKey(groupKey)
        .withValue(
            ImmutableGroupRecordValue.builder()
                .from((GroupRecordValue) recordValueRecord.getValue())
                .withGroupKey(groupKey)
                .withGroupId(groupId)
                .withEntityId(entityId != null ? entityId : "0")
                .withEntityType(entityId != null ? EntityType.USER : null)
                .build())
        .build();
  }

  public ImmutableRecord<RecordValue> getIncidentRecord(
      final IncidentIntent intent,
      final long incidentKey,
      final long processInstanceKey,
      final long rootProcessInstanceKey,
      final long elementInstanceKey) {
    final Record<RecordValue> recordValueRecord = FACTORY.generateRecord(ValueType.INCIDENT);
    return ImmutableRecord.builder()
        .from(recordValueRecord)
        .withKey(incidentKey)
        .withIntent(intent)
        .withPosition(nextPosition())
        .withPartitionId(1)
        .withTimestamp(System.currentTimeMillis())
        .withValue(
            ImmutableIncidentRecordValue.builder()
                .from((IncidentRecordValue) recordValueRecord.getValue())
                .withElementInstanceKey(elementInstanceKey)
                .withProcessInstanceKey(processInstanceKey)
                .withRootProcessInstanceKey(rootProcessInstanceKey)
                .withElementInstancePath(List.of(List.of(processInstanceKey, elementInstanceKey)))
                .build())
        .build();
  }

  public ImmutableRecord<RecordValue> getHistoryDeletionRecord(
      final HistoryDeletionIntent intent,
      final long resourceKey,
      final HistoryDeletionType deletionType) {
    final Record<RecordValue> recordValueRecord =
        FACTORY.generateRecord(ValueType.HISTORY_DELETION);
    return ImmutableRecord.builder()
        .from(recordValueRecord)
        .withKey(resourceKey)
        .withIntent(intent)
        .withPosition(nextPosition())
        .withPartitionId(1)
        .withTimestamp(System.currentTimeMillis())
        .withValue(
            ImmutableHistoryDeletionRecordValue.builder()
                .from((ImmutableHistoryDeletionRecordValue) recordValueRecord.getValue())
                .withResourceKey(resourceKey)
                .withResourceType(deletionType)
                .build())
        .build();
  }

  public ImmutableRecord<RecordValue> getAuthorizationRecord(
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
        .withPosition(nextPosition())
        .withPartitionId(1)
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

  public ImmutableRecord<RecordValue> getBatchOperationCreatedRecord() {
    return getBatchOperationCreatedRecord(BatchOperationType.CANCEL_PROCESS_INSTANCE);
  }

  public ImmutableRecord<RecordValue> getBatchOperationCreatedRecord(
      final BatchOperationType type) {
    final Record<RecordValue> recordValueRecord =
        FACTORY.generateRecord(ValueType.BATCH_OPERATION_CREATION);

    return ImmutableRecord.builder()
        .from(recordValueRecord)
        .withIntent(BatchOperationIntent.CREATED)
        .withPosition(nextPosition())
        .withPartitionId(1)
        .withTimestamp(System.currentTimeMillis())
        .withValue(
            ImmutableBatchOperationCreationRecordValue.builder()
                .from((ImmutableBatchOperationCreationRecordValue) recordValueRecord.getValue())
                .withBatchOperationType(type)
                .build())
        .build();
  }

  public ImmutableRecord<RecordValue> getBatchOperationInitializedRecord(
      final Long batchOperationKey) {
    final Record<RecordValue> recordValueRecord =
        FACTORY.generateRecord(ValueType.BATCH_OPERATION_INITIALIZATION);

    return ImmutableRecord.builder()
        .from(recordValueRecord)
        .withIntent(BatchOperationIntent.INITIALIZED)
        .withPosition(nextPosition())
        .withPartitionId(1)
        .withTimestamp(System.currentTimeMillis())
        .withValue(
            ImmutableBatchOperationInitializationRecordValue.builder()
                .from(
                    (ImmutableBatchOperationInitializationRecordValue) recordValueRecord.getValue())
                .withBatchOperationKey(batchOperationKey)
                .build())
        .build();
  }

  public ImmutableRecord<RecordValue> getBatchOperationChunkRecord(final Long batchOperationKey) {
    final Record<RecordValue> recordValueRecord =
        FACTORY.generateRecord(ValueType.BATCH_OPERATION_CHUNK);

    return ImmutableRecord.builder()
        .from(recordValueRecord)
        .withIntent(BatchOperationChunkIntent.CREATE)
        .withPosition(nextPosition())
        .withPartitionId(1)
        .withTimestamp(System.currentTimeMillis())
        .withValue(
            ImmutableBatchOperationChunkRecordValue.builder()
                .from((ImmutableBatchOperationChunkRecordValue) recordValueRecord.getValue())
                .withBatchOperationKey(batchOperationKey)
                .withItems(
                    List.of(
                        ImmutableBatchOperationItemValue.builder()
                            .withItemKey(1L)
                            .withProcessInstanceKey(1L)
                            .build(),
                        ImmutableBatchOperationItemValue.builder()
                            .withItemKey(2L)
                            .withProcessInstanceKey(2L)
                            .build(),
                        ImmutableBatchOperationItemValue.builder()
                            .withItemKey(3L)
                            .withProcessInstanceKey(3L)
                            .build()))
                .build())
        .build();
  }

  protected Record<RecordValue> getBatchOperationCompletedRecord(final Long batchOperationKey) {
    return generateLifecycleRecord(batchOperationKey, BatchOperationIntent.COMPLETED, false);
  }

  protected Record<RecordValue> getBatchOperationCompletedWithErrorsRecord(
      final Long batchOperationKey) {
    return generateLifecycleRecord(batchOperationKey, BatchOperationIntent.COMPLETED, true);
  }

  protected Record<RecordValue> getBatchOperationLifecycleCanceledRecord(
      final Long batchOperationKey) {
    return generateLifecycleRecord(batchOperationKey, BatchOperationIntent.CANCELED, false);
  }

  protected Record<RecordValue> getBatchOperationLifecycleSuspendedRecord(
      final Long batchOperationKey) {
    return generateLifecycleRecord(batchOperationKey, BatchOperationIntent.SUSPENDED, false);
  }

  protected Record<RecordValue> getBatchOperationLifecycleResumeRecord(
      final Long batchOperationKey) {
    return generateLifecycleRecord(batchOperationKey, BatchOperationIntent.RESUMED, false);
  }

  protected Record<RecordValue> generateLifecycleRecord(
      final Long batchOperationKey, final BatchOperationIntent intent, final boolean withErrors) {
    final List<BatchOperationErrorValue> errors;
    if (withErrors) {
      errors = List.of(FACTORY.generateObject(BatchOperationErrorValue.class));
    } else {
      errors = emptyList();
    }
    final var value =
        FACTORY
            .generateObject(ImmutableBatchOperationLifecycleManagementRecordValue.class)
            .withBatchOperationKey(batchOperationKey)
            .withErrors(errors);

    return FACTORY.generateRecord(
        ValueType.BATCH_OPERATION_LIFECYCLE_MANAGEMENT,
        v ->
            v.withPosition(nextPosition())
                .withPartitionId(1)
                .withTimestamp(System.currentTimeMillis())
                .withValue(value),
        intent);
  }

  public ImmutableRecord<RecordValue> getCanceledProcessRecord(
      final Long processInstanceKey, final Long batchOperationKey) {
    final Record<RecordValue> recordValueRecord =
        FACTORY.generateRecord(ValueType.PROCESS_INSTANCE);

    return ImmutableRecord.builder()
        .from(recordValueRecord)
        .withIntent(ProcessInstanceIntent.ELEMENT_TERMINATED)
        .withKey(processInstanceKey)
        .withPosition(nextPosition())
        .withPartitionId(1)
        .withTimestamp(System.currentTimeMillis())
        .withBatchOperationReference(batchOperationKey)
        .withValue(
            ImmutableProcessInstanceRecordValue.builder()
                .from((ImmutableProcessInstanceRecordValue) recordValueRecord.getValue())
                .withBpmnElementType(BpmnElementType.PROCESS)
                .withProcessInstanceKey(processInstanceKey)
                .withParentProcessInstanceKey(-1)
                .build())
        .build();
  }

  public ImmutableRecord<RecordValue> getRejectedCancelProcessRecord(
      final Long processInstanceKey, final Long batchOperationKey) {
    final Record<RecordValue> recordValueRecord =
        FACTORY.generateRecord(ValueType.PROCESS_INSTANCE);

    return ImmutableRecord.builder()
        .from(recordValueRecord)
        .withIntent(ProcessInstanceIntent.CANCEL)
        .withRejectionType(RejectionType.INVALID_STATE)
        .withKey(processInstanceKey)
        .withPosition(nextPosition())
        .withPartitionId(1)
        .withTimestamp(System.currentTimeMillis())
        .withBatchOperationReference(batchOperationKey)
        .withValue(
            ImmutableProcessInstanceRecordValue.builder()
                .from((ImmutableProcessInstanceRecordValue) recordValueRecord.getValue())
                .withBpmnElementType(BpmnElementType.PROCESS)
                .withProcessInstanceKey(processInstanceKey)
                .withParentProcessInstanceKey(-1)
                .build())
        .build();
  }

  public ImmutableRecord<RecordValue> getBatchOperationResolveIncidentRecord(
      final Long incidentKey, final Long batchOperationKey) {
    final Record<RecordValue> recordValueRecord = FACTORY.generateRecord(ValueType.INCIDENT);

    return ImmutableRecord.builder()
        .from(recordValueRecord)
        .withIntent(IncidentIntent.RESOLVED)
        .withKey(incidentKey)
        .withPosition(nextPosition())
        .withPartitionId(1)
        .withTimestamp(System.currentTimeMillis())
        .withBatchOperationReference(batchOperationKey)
        .build();
  }

  public ImmutableRecord<RecordValue> getFailedBatchOperationResolveIncidentRecord(
      final Long incidentKey, final Long batchOperationKey) {
    final Record<RecordValue> recordValueRecord = FACTORY.generateRecord(ValueType.INCIDENT);

    return ImmutableRecord.builder()
        .from(recordValueRecord)
        .withIntent(IncidentIntent.RESOLVE)
        .withRejectionType(RejectionType.INVALID_STATE)
        .withKey(incidentKey)
        .withPosition(nextPosition())
        .withPartitionId(1)
        .withTimestamp(System.currentTimeMillis())
        .withBatchOperationReference(batchOperationKey)
        .build();
  }

  public ImmutableRecord<RecordValue> getBatchOperationProcessMigratedRecord(
      final Long processInstanceKey, final Long batchOperationKey) {
    final Record<RecordValue> recordValueRecord =
        FACTORY.generateRecord(ValueType.PROCESS_INSTANCE_MIGRATION);

    return ImmutableRecord.builder()
        .from(recordValueRecord)
        .withIntent(ProcessInstanceMigrationIntent.MIGRATED)
        .withKey(processInstanceKey)
        .withPosition(nextPosition())
        .withPartitionId(1)
        .withTimestamp(System.currentTimeMillis())
        .withBatchOperationReference(batchOperationKey)
        .withValue(
            ImmutableProcessInstanceMigrationRecordValue.builder()
                .from((ImmutableProcessInstanceMigrationRecordValue) recordValueRecord.getValue())
                .withProcessInstanceKey(processInstanceKey)
                .build())
        .build();
  }

  public ImmutableRecord<RecordValue> getFailedBatchOperationProcessMigratedRecord(
      final Long processInstanceKey, final Long batchOperationKey) {
    final Record<RecordValue> recordValueRecord =
        FACTORY.generateRecord(ValueType.PROCESS_INSTANCE_MIGRATION);

    return ImmutableRecord.builder()
        .from(recordValueRecord)
        .withIntent(ProcessInstanceMigrationIntent.MIGRATE)
        .withRejectionType(RejectionType.INVALID_STATE)
        .withKey(processInstanceKey)
        .withPosition(nextPosition())
        .withPartitionId(1)
        .withTimestamp(System.currentTimeMillis())
        .withBatchOperationReference(batchOperationKey)
        .withValue(
            ImmutableProcessInstanceMigrationRecordValue.builder()
                .from((ImmutableProcessInstanceMigrationRecordValue) recordValueRecord.getValue())
                .withProcessInstanceKey(processInstanceKey)
                .build())
        .build();
  }

  public ImmutableRecord<RecordValue> getBatchOperationModifyProcessInstanceRecord(
      final Long processInstanceKey, final Long batchOperationKey) {
    final Record<RecordValue> recordValueRecord =
        FACTORY.generateRecord(ValueType.PROCESS_INSTANCE_MODIFICATION);

    return ImmutableRecord.builder()
        .from(recordValueRecord)
        .withIntent(ProcessInstanceModificationIntent.MODIFIED)
        .withKey(processInstanceKey)
        .withPosition(nextPosition())
        .withPartitionId(1)
        .withTimestamp(System.currentTimeMillis())
        .withBatchOperationReference(batchOperationKey)
        .withValue(
            ImmutableProcessInstanceModificationRecordValue.builder()
                .from(
                    (ImmutableProcessInstanceModificationRecordValue) recordValueRecord.getValue())
                .withProcessInstanceKey(processInstanceKey)
                .build())
        .build();
  }

  public ImmutableRecord<RecordValue> getFailedBatchOperationModifyProcessInstanceRecord(
      final Long processInstanceKey, final Long batchOperationKey) {
    final Record<RecordValue> recordValueRecord =
        FACTORY.generateRecord(ValueType.PROCESS_INSTANCE_MODIFICATION);

    return ImmutableRecord.builder()
        .from(recordValueRecord)
        .withIntent(ProcessInstanceModificationIntent.MODIFY)
        .withRejectionType(RejectionType.INVALID_STATE)
        .withKey(processInstanceKey)
        .withPosition(nextPosition())
        .withPartitionId(1)
        .withTimestamp(System.currentTimeMillis())
        .withBatchOperationReference(batchOperationKey)
        .withValue(
            ImmutableProcessInstanceModificationRecordValue.builder()
                .from(
                    (ImmutableProcessInstanceModificationRecordValue) recordValueRecord.getValue())
                .withProcessInstanceKey(processInstanceKey)
                .build())
        .build();
  }
}
