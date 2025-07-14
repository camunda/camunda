/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.write.domain.UserTaskDbModel;
import io.camunda.db.rdbms.write.domain.UserTaskDbModel.UserTaskState;
import io.camunda.db.rdbms.write.domain.UserTaskMigrationDbModel;
import io.camunda.db.rdbms.write.service.UserTaskWriter;
import io.camunda.zeebe.exporter.common.cache.ExporterEntityCache;
import io.camunda.zeebe.exporter.common.cache.process.CachedProcessEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import io.camunda.zeebe.util.DateUtil;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserTaskExportHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();

  @Mock private UserTaskWriter userTaskWriter;
  @Mock private ExporterEntityCache<Long, CachedProcessEntity> processCache;

  @Captor private ArgumentCaptor<UserTaskDbModel> taskDbModelCaptor;

  private UserTaskExportHandler handler;

  @BeforeEach
  void setUp() {
    handler = new UserTaskExportHandler(userTaskWriter, processCache);
  }

  @ParameterizedTest(name = "should be able to export record with intent: {0}")
  @EnumSource(
      value = UserTaskIntent.class,
      names = {"CREATED", "ASSIGNED", "UPDATED", "COMPLETED", "CANCELED", "MIGRATED"})
  void shouldExportRecord(final UserTaskIntent intent) {
    // given
    final Record<UserTaskRecordValue> record =
        factory.generateRecord(ValueType.USER_TASK, r -> r.withIntent(intent));

    // when - then
    assertThat(handler.canExport(record))
        .as("Handler should be able to export record with intent: %s", intent)
        .isTrue();
  }

  @ParameterizedTest(name = "should not export record with unsupported intent: {0}")
  @EnumSource(
      value = UserTaskIntent.class,
      // Exclude the intents that are supported by the handler
      mode = EnumSource.Mode.EXCLUDE,
      names = {"CREATED", "ASSIGNED", "UPDATED", "COMPLETED", "CANCELED", "MIGRATED"})
  void shouldNotExportRecord(final UserTaskIntent intent) {
    // given
    final Record<UserTaskRecordValue> record =
        factory.generateRecord(ValueType.USER_TASK, r -> r.withIntent(intent));

    // when - then
    assertThat(handler.canExport(record))
        .as("Handler should not be able to export record with unsupported intent: %s", intent)
        // If this assertion fails, it means that the given intent was recently added to
        // `UserTaskExportHandler#EXPORTABLE_INTENTS`.
        // In that case:
        // - Add it to the supported intents list in both this test and the one above
        // - Review whether it needs custom handling in `UserTaskExportHandler#export`:
        //   - If so, add a dedicated handling in `UserTaskExportHandler#export`
        //   - Add a dedicated test for the new intent in this class
        .isFalse();
  }

  @Test
  @DisplayName("Should handle user task record with CREATED intent")
  void shouldHandleCreatedUserTaskRecord() {
    // given
    final Record<UserTaskRecordValue> record =
        factory.generateRecord(ValueType.USER_TASK, r -> r.withIntent(UserTaskIntent.CREATED));
    final var recordValue = record.getValue();
    when(processCache.get(recordValue.getProcessDefinitionKey())).thenReturn(Optional.empty());

    // when
    handler.export(record);

    // then
    verify(userTaskWriter).create(taskDbModelCaptor.capture());
    assertThat(taskDbModelCaptor.getValue())
        .satisfies(
            model -> {
              assertThat(model.state())
                  .as("State should be CREATED for record with CREATED intent")
                  .isEqualTo(UserTaskState.CREATED);
              assertThat(model.assignee())
                  .as("Assignee should be same as in the record")
                  .isNotNull()
                  .isEqualTo(recordValue.getAssignee());
              assertThat(model.completionDate())
                  .as("Completion date should be null for CREATED intent")
                  .isNull();

              assertUserTaskModelFieldsEqualToRecord(model, record);
            });

    // ensure no other methods are called
    verifyNoMoreInteractions(userTaskWriter);
  }

  @Test
  @DisplayName("Should handle user task record with ASSIGNED intent")
  void shouldHandleAssignedUserTaskRecord() {
    // given
    final Record<UserTaskRecordValue> record =
        factory.generateRecord(ValueType.USER_TASK, r -> r.withIntent(UserTaskIntent.ASSIGNED));
    final var recordValue = record.getValue();
    when(processCache.get(recordValue.getProcessDefinitionKey())).thenReturn(Optional.empty());

    // when
    handler.export(record);

    // then
    verify(userTaskWriter).update(taskDbModelCaptor.capture());
    assertThat(taskDbModelCaptor.getValue())
        .satisfies(
            model -> {
              assertThat(model.state())
                  .as("State should be null for record with ASSIGNED intent")
                  .isNull();
              assertThat(model.assignee())
                  .as("Assignee should be same as in the record")
                  .isNotNull()
                  .isEqualTo(recordValue.getAssignee());
              assertThat(model.completionDate())
                  .as("Completion date should be null for ASSIGNED intent")
                  .isNull();
              assertUserTaskModelFieldsEqualToRecord(model, record);
            });

    // ensure no other methods are called
    verifyNoMoreInteractions(userTaskWriter);
  }

  @Test
  @DisplayName("Should handle user task record with UPDATED intent")
  void shouldHandleUpdatedUserTaskRecord() {
    // given
    final Record<UserTaskRecordValue> record =
        factory.generateRecord(ValueType.USER_TASK, r -> r.withIntent(UserTaskIntent.UPDATED));
    final var recordValue = record.getValue();
    when(processCache.get(recordValue.getProcessDefinitionKey())).thenReturn(Optional.empty());

    // when
    handler.export(record);

    // then
    verify(userTaskWriter).update(taskDbModelCaptor.capture());
    assertThat(taskDbModelCaptor.getValue())
        .satisfies(
            model -> {
              assertThat(model.state())
                  .as("State should be null for record with UPDATED intent")
                  .isNull();
              assertThat(model.assignee())
                  .as("Assignee should be same as in the record")
                  .isNotNull()
                  .isEqualTo(recordValue.getAssignee());
              assertThat(model.completionDate())
                  .as("Completion date should be null for UPDATED intent")
                  .isNull();

              assertUserTaskModelFieldsEqualToRecord(model, record);
            });

    // ensure no other methods are called
    verifyNoMoreInteractions(userTaskWriter);
  }

  @Test
  @DisplayName("Should handle user task record with COMPLETED intent")
  void shouldHandleCompletedUserTaskRecord() {
    // given
    final Record<UserTaskRecordValue> record =
        factory.generateRecord(ValueType.USER_TASK, r -> r.withIntent(UserTaskIntent.COMPLETED));
    final var recordValue = record.getValue();
    when(processCache.get(recordValue.getProcessDefinitionKey())).thenReturn(Optional.empty());

    // when
    handler.export(record);

    // then
    verify(userTaskWriter).update(taskDbModelCaptor.capture());
    assertThat(taskDbModelCaptor.getValue())
        .satisfies(
            model -> {
              assertThat(model.state())
                  .as("State should be COMPLETED for record with COMPLETED intent")
                  .isEqualTo(UserTaskState.COMPLETED);
              assertThat(model.assignee())
                  .as("Assignee should be same as in the record")
                  .isNotNull()
                  .isEqualTo(recordValue.getAssignee());
              assertThat(model.completionDate())
                  .as("Completion date should be not null for COMPLETED intent")
                  .isNotNull()
                  .isEqualTo(DateUtil.toOffsetDateTime(record.getTimestamp()));

              assertUserTaskModelFieldsEqualToRecord(model, record);
            });

    // ensure no other methods are called
    verifyNoMoreInteractions(userTaskWriter);
  }

  @Test
  @DisplayName("Should handle user task record with CANCELED intent")
  void shouldHandleCanceledUserTaskRecord() {
    // given
    final Record<UserTaskRecordValue> record =
        factory.generateRecord(ValueType.USER_TASK, r -> r.withIntent(UserTaskIntent.CANCELED));
    final var recordValue = record.getValue();
    when(processCache.get(recordValue.getProcessDefinitionKey())).thenReturn(Optional.empty());

    // when
    handler.export(record);

    // then
    verify(userTaskWriter).update(taskDbModelCaptor.capture());
    assertThat(taskDbModelCaptor.getValue())
        .satisfies(
            model -> {
              assertThat(model.state())
                  .as("State should be CANCELED for record with CANCELED intent")
                  .isEqualTo(UserTaskState.CANCELED);
              assertThat(model.assignee())
                  .as("Assignee should be same as in the record")
                  .isNotNull()
                  .isEqualTo(recordValue.getAssignee());
              assertThat(model.completionDate())
                  .as("Completion date should be not null for CANCELED intent")
                  .isNotNull()
                  .isEqualTo(DateUtil.toOffsetDateTime(record.getTimestamp()));

              assertUserTaskModelFieldsEqualToRecord(model, record);
            });

    // ensure no other methods are called
    verifyNoMoreInteractions(userTaskWriter);
  }

  @Test
  @DisplayName("Should handle user task record with MIGRATED intent")
  void shouldHandleMigratedUserTaskRecord() {
    // given
    final Record<UserTaskRecordValue> record =
        factory.generateRecord(ValueType.USER_TASK, r -> r.withIntent(UserTaskIntent.MIGRATED));
    final var recordValue = record.getValue();
    when(processCache.get(recordValue.getProcessDefinitionKey())).thenReturn(Optional.empty());

    // when
    handler.export(record);

    // then
    final var taskMigrationDbModelCaptor = ArgumentCaptor.forClass(UserTaskMigrationDbModel.class);
    verify(userTaskWriter).migrateToProcess(taskMigrationDbModelCaptor.capture());
    assertThat(taskMigrationDbModelCaptor.getValue())
        .satisfies(
            migrationModel -> {
              assertThat(migrationModel.userTaskKey())
                  .as("User task key should be same as in the record")
                  .isEqualTo(recordValue.getUserTaskKey());
              assertThat(migrationModel.processDefinitionKey())
                  .as("Process definition key should be same as in the record")
                  .isEqualTo(recordValue.getProcessDefinitionKey());
              assertThat(migrationModel.processDefinitionId())
                  .as("Process definition ID should be same as in the record")
                  .isEqualTo(recordValue.getBpmnProcessId());
              assertThat(migrationModel.elementId())
                  .as("Element ID should be same as in the record")
                  .isEqualTo(recordValue.getElementId());
              assertThat(migrationModel.name()).isNull();
              assertThat(migrationModel.processDefinitionVersion())
                  .as("Process definition version should be same as in the record")
                  .isEqualTo(recordValue.getProcessDefinitionVersion());
            });

    // ensure no other methods are called
    verifyNoMoreInteractions(userTaskWriter);
  }

  // Helper method to assert that the fields of a UserTaskDbModel match the values in a Record and
  // RecordValue, except for the 'state', 'assignee', and 'completionDate' fields,
  // which are handled separately
  private static void assertUserTaskModelFieldsEqualToRecord(
      final UserTaskDbModel model, final Record<UserTaskRecordValue> record) {
    final var recordValue = record.getValue();
    assertThat(model.userTaskKey()).isEqualTo(recordValue.getUserTaskKey());
    assertThat(model.elementId()).isEqualTo(recordValue.getElementId());
    assertThat(model.processDefinitionId()).isEqualTo(recordValue.getBpmnProcessId());
    assertThat(model.creationDate())
        .isNotNull()
        .isEqualTo(DateUtil.toOffsetDateTime(recordValue.getCreationTimestamp()));
    assertThat(model.formKey()).isEqualTo(recordValue.getFormKey());
    assertThat(model.processDefinitionKey()).isEqualTo(recordValue.getProcessDefinitionKey());
    assertThat(model.processInstanceKey()).isEqualTo(recordValue.getProcessInstanceKey());
    assertThat(model.elementInstanceKey()).isEqualTo(recordValue.getElementInstanceKey());
    assertThat(model.tenantId()).isEqualTo(recordValue.getTenantId());
    assertThat(model.candidateGroups()).isEqualTo(recordValue.getCandidateGroupsList());
    assertThat(model.candidateUsers()).isEqualTo(recordValue.getCandidateUsersList());
    assertThat(model.externalFormReference()).isEqualTo(recordValue.getExternalFormReference());
    assertThat(model.processDefinitionVersion())
        .isEqualTo(recordValue.getProcessDefinitionVersion());
    assertThat(model.customHeaders()).isEqualTo(recordValue.getCustomHeaders());
    assertThat(model.priority()).isEqualTo(recordValue.getPriority());
    assertThat(model.partitionId()).isEqualTo(record.getPartitionId());
    assertThat(model.dueDate()).isNotNull().isEqualTo(recordValue.getDueDate());
    assertThat(model.followUpDate()).isNotNull().isEqualTo(recordValue.getFollowUpDate());
  }
}
