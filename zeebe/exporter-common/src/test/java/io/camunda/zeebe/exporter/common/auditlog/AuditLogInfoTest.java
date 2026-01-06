/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.auditlog;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.entities.AuditLogEntity.AuditLogActorType;
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationType;
import io.camunda.search.entities.AuditLogEntity.AuditLogTenantScope;
import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.intent.DecisionEvaluationIntent;
import io.camunda.zeebe.protocol.record.intent.DecisionIntent;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.MappingRuleIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceMigrationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent;
import io.camunda.zeebe.protocol.record.intent.ResourceIntent;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableUserTaskRecordValue;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AuditLogInfoTest {

  private final ProtocolFactory factory = new ProtocolFactory();

  @Test
  void shouldMapActorFromUsername() {
    final var record =
        factory.generateRecord(
            ValueType.PROCESS_INSTANCE_MODIFICATION,
            r ->
                r.withIntent(ProcessInstanceModificationIntent.MODIFIED)
                    .withAuthorizations(Map.of(Authorization.AUTHORIZED_USERNAME, "test-user")));

    final var info = AuditLogInfo.of(record);

    assertThat(info.actor()).isNotNull();
    assertThat(info.actor().actorType()).isEqualTo(AuditLogActorType.USER);
    assertThat(info.actor().actorId()).isEqualTo("test-user");
  }

  @Test
  void shouldMapActorFromClientId() {
    final var record =
        factory.generateRecord(
            ValueType.PROCESS_INSTANCE_MODIFICATION,
            r ->
                r.withIntent(ProcessInstanceModificationIntent.MODIFIED)
                    .withAuthorizations(Map.of(Authorization.AUTHORIZED_CLIENT_ID, "test-client")));

    final var info = AuditLogInfo.of(record);

    assertThat(info.actor()).isNotNull();
    assertThat(info.actor().actorType()).isEqualTo(AuditLogActorType.CLIENT);
    assertThat(info.actor().actorId()).isEqualTo("test-client");
  }

  @Test
  void shouldMapActorFromAnonymous() {
    final var record =
        factory.generateRecord(
            ValueType.PROCESS_INSTANCE_MODIFICATION,
            r ->
                r.withIntent(ProcessInstanceModificationIntent.MODIFIED)
                    .withAuthorizations(Map.of(Authorization.AUTHORIZED_ANONYMOUS_USER, true)));

    final var info = AuditLogInfo.of(record);

    assertThat(info.actor()).isNotNull();
    assertThat(info.actor().actorType()).isEqualTo(AuditLogActorType.ANONYMOUS);
    assertThat(info.actor().actorId()).isEqualTo(null);
  }

  @Test
  void shouldReturnUnknownActorWhenNoAuthorizationProvided() {
    final var record =
        factory.generateRecordWithIntent(
            ValueType.PROCESS_INSTANCE_MODIFICATION, ProcessInstanceModificationIntent.MODIFIED);

    final var info = AuditLogInfo.of(record);

    assertThat(info.actor()).isNotNull();
    assertThat(info.actor().actorType()).isEqualTo(AuditLogActorType.UNKNOWN);
    assertThat(info.actor().actorId()).isEqualTo(null);
  }

  @Test
  void shouldMapTenantFromTenantOwnedRecord() {
    final var record =
        factory.generateRecord(
            ValueType.PROCESS_INSTANCE_MODIFICATION,
            r ->
                r.withIntent(ProcessInstanceModificationIntent.MODIFIED)
                    .withValue(new ProcessInstanceCreationRecord().setTenantId("tenant-1")));

    final var info = AuditLogInfo.of(record);

    assertThat(info.tenant()).isPresent();
    assertThat(info.tenant().get().tenantId()).isEqualTo("tenant-1");
    assertThat(info.tenant().get().scope()).isEqualTo(AuditLogTenantScope.TENANT);
  }

  @Test
  void shouldMapTenantFromNonTenantOwnedRecord() {
    final var record = factory.generateRecord(ValueType.AUTHORIZATION);

    final var info = AuditLogInfo.of(record);

    assertThat(info.tenant()).isNotPresent();
  }

  @Test
  public void shouldReturnAssignForAssignedUserTaskIntentWithAssignee() {
    // when
    final Record<UserTaskRecordValue> record =
        factory.generateRecord(ValueType.USER_TASK, r -> r.withIntent(UserTaskIntent.ASSIGNED));

    final UserTaskRecordValue value = (UserTaskRecordValue) record.getValue();

    // then
    final var info = AuditLogInfo.of(record);
    assertThat(info.operationType()).isEqualTo(AuditLogOperationType.ASSIGN);
  }

  @Test
  public void shouldReturnUnassignForAssignedUserTaskIntentWithNullAssignee() {
    // when
    final UserTaskRecordValue recordValue =
        ImmutableUserTaskRecordValue.builder()
            .from(factory.generateObject(UserTaskRecordValue.class))
            .withAssignee(null)
            .build();

    final Record<UserTaskRecordValue> record =
        factory.generateRecord(
            ValueType.USER_TASK, r -> r.withIntent(UserTaskIntent.ASSIGNED).withValue(recordValue));

    final UserTaskRecordValue value = (UserTaskRecordValue) record.getValue();

    // then
    final var info = AuditLogInfo.of(record);
    assertThat(info.operationType()).isEqualTo(AuditLogOperationType.UNASSIGN);
  }

  @Test
  public void shouldReturnUnassignForAssignedUserTaskIntentWithBlankAssignee() {
    // when
    final UserTaskRecordValue recordValue =
        ImmutableUserTaskRecordValue.builder()
            .from(factory.generateObject(UserTaskRecordValue.class))
            .withAssignee("")
            .build();

    final Record<UserTaskRecordValue> record =
        factory.generateRecord(
            ValueType.USER_TASK, r -> r.withIntent(UserTaskIntent.ASSIGNED).withValue(recordValue));

    final UserTaskRecordValue value = (UserTaskRecordValue) record.getValue();

    // then
    final var info = AuditLogInfo.of(record);
    assertThat(info.operationType()).isEqualTo(AuditLogOperationType.UNASSIGN);
  }

  public static Stream<Arguments> getIntentMappings() {
    return Stream.of(
        Arguments.of(AuthorizationIntent.CREATED, AuditLogOperationType.CREATE),
        Arguments.of(AuthorizationIntent.DELETED, AuditLogOperationType.DELETE),
        Arguments.of(AuthorizationIntent.UPDATED, AuditLogOperationType.UPDATE),
        Arguments.of(BatchOperationIntent.CANCEL, AuditLogOperationType.CANCEL),
        Arguments.of(BatchOperationIntent.CANCELED, AuditLogOperationType.CANCEL),
        Arguments.of(BatchOperationIntent.CREATED, AuditLogOperationType.CREATE),
        Arguments.of(BatchOperationIntent.RESUME, AuditLogOperationType.RESUME),
        Arguments.of(BatchOperationIntent.RESUMED, AuditLogOperationType.RESUME),
        Arguments.of(BatchOperationIntent.SUSPEND, AuditLogOperationType.SUSPEND),
        Arguments.of(BatchOperationIntent.SUSPENDED, AuditLogOperationType.SUSPEND),
        Arguments.of(DecisionIntent.CREATED, AuditLogOperationType.CREATE),
        Arguments.of(DecisionIntent.DELETED, AuditLogOperationType.DELETE),
        Arguments.of(DecisionEvaluationIntent.EVALUATED, AuditLogOperationType.EVALUATE),
        Arguments.of(DecisionEvaluationIntent.FAILED, AuditLogOperationType.EVALUATE),
        Arguments.of(GroupIntent.CREATED, AuditLogOperationType.CREATE),
        Arguments.of(GroupIntent.UPDATED, AuditLogOperationType.UPDATE),
        Arguments.of(GroupIntent.DELETED, AuditLogOperationType.DELETE),
        Arguments.of(GroupIntent.ENTITY_ADDED, AuditLogOperationType.ASSIGN),
        Arguments.of(GroupIntent.ENTITY_REMOVED, AuditLogOperationType.UNASSIGN),
        Arguments.of(IncidentIntent.RESOLVED, AuditLogOperationType.RESOLVE),
        Arguments.of(IncidentIntent.RESOLVE, AuditLogOperationType.RESOLVE),
        Arguments.of(MappingRuleIntent.CREATED, AuditLogOperationType.CREATE),
        Arguments.of(MappingRuleIntent.UPDATED, AuditLogOperationType.UPDATE),
        Arguments.of(MappingRuleIntent.DELETED, AuditLogOperationType.DELETE),
        Arguments.of(ProcessInstanceIntent.CANCELING, AuditLogOperationType.CANCEL),
        Arguments.of(ProcessInstanceIntent.CANCEL, AuditLogOperationType.CANCEL),
        Arguments.of(ProcessInstanceCreationIntent.CREATED, AuditLogOperationType.CREATE),
        Arguments.of(ProcessInstanceMigrationIntent.MIGRATED, AuditLogOperationType.MIGRATE),
        Arguments.of(ProcessInstanceMigrationIntent.MIGRATE, AuditLogOperationType.MIGRATE),
        Arguments.of(ProcessInstanceModificationIntent.MODIFIED, AuditLogOperationType.MODIFY),
        Arguments.of(ProcessInstanceModificationIntent.MODIFY, AuditLogOperationType.MODIFY),
        Arguments.of(ResourceIntent.CREATED, AuditLogOperationType.CREATE),
        Arguments.of(ResourceIntent.DELETED, AuditLogOperationType.DELETE),
        Arguments.of(RoleIntent.CREATED, AuditLogOperationType.CREATE),
        Arguments.of(RoleIntent.UPDATED, AuditLogOperationType.UPDATE),
        Arguments.of(RoleIntent.DELETED, AuditLogOperationType.DELETE),
        Arguments.of(RoleIntent.ENTITY_ADDED, AuditLogOperationType.ASSIGN),
        Arguments.of(RoleIntent.ENTITY_REMOVED, AuditLogOperationType.UNASSIGN),
        Arguments.of(TenantIntent.CREATED, AuditLogOperationType.CREATE),
        Arguments.of(TenantIntent.ENTITY_ADDED, AuditLogOperationType.ASSIGN),
        Arguments.of(TenantIntent.ENTITY_REMOVED, AuditLogOperationType.UNASSIGN),
        Arguments.of(TenantIntent.UPDATED, AuditLogOperationType.UPDATE),
        Arguments.of(TenantIntent.DELETED, AuditLogOperationType.DELETE),
        Arguments.of(UserIntent.CREATED, AuditLogOperationType.CREATE),
        Arguments.of(UserIntent.UPDATED, AuditLogOperationType.UPDATE),
        Arguments.of(UserIntent.DELETED, AuditLogOperationType.DELETE),
        Arguments.of(UserTaskIntent.UPDATED, AuditLogOperationType.UPDATE),
        // type is different for ASSIGNED intent, validating that it returns UNKNOWN here
        Arguments.of(UserTaskIntent.ASSIGNED, AuditLogOperationType.UNKNOWN),
        Arguments.of(UserTaskIntent.COMPLETED, AuditLogOperationType.COMPLETE),
        Arguments.of(UserTaskIntent.UPDATE, AuditLogOperationType.UPDATE),
        Arguments.of(UserTaskIntent.ASSIGN, AuditLogOperationType.ASSIGN),
        Arguments.of(UserTaskIntent.COMPLETE, AuditLogOperationType.COMPLETE),
        Arguments.of(VariableIntent.CREATED, AuditLogOperationType.CREATE),
        Arguments.of(VariableIntent.UPDATED, AuditLogOperationType.UPDATE));
  }

  @MethodSource("getIntentMappings")
  @ParameterizedTest
  void shouldMapCorrectOperationType(
      final Intent intent, final AuditLogOperationType operationType) {
    final Record<?> recordValue = factory.generateRecord(ValueType.JOB, r -> r.withIntent(intent));

    final AuditLogInfo auditLogInfo = AuditLogInfo.of(recordValue);
    assertThat(auditLogInfo.operationType()).isEqualTo(operationType);
  }
}
