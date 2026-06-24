/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
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
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserTaskExportHandlerTest {

  private static final Set<UserTaskIntent> TEST_EXPORTABLE_INTENTS =
      EnumSet.of(
          UserTaskIntent.CREATING,
          UserTaskIntent.CREATED,
          UserTaskIntent.ASSIGNING,
          UserTaskIntent.CLAIMING,
          UserTaskIntent.ASSIGNED,
          UserTaskIntent.UPDATING,
          UserTaskIntent.UPDATED,
          UserTaskIntent.COMPLETING,
          UserTaskIntent.COMPLETED,
          UserTaskIntent.CANCELING,
          UserTaskIntent.CANCELED,
          UserTaskIntent.MIGRATED,
          UserTaskIntent.ASSIGNMENT_DENIED,
          UserTaskIntent.UPDATE_DENIED,
          UserTaskIntent.COMPLETION_DENIED);

  private final ProtocolFactory factory = new ProtocolFactory();

  @Mock private UserTaskWriter userTaskWriter;
  @Mock private ExporterEntityCache<Long, CachedProcessEntity> processCache;

  @Captor private ArgumentCaptor<UserTaskDbModel> taskDbModelCaptor;

  private UserTaskExportHandler handler;

  @BeforeEach
  void setUp() {
    handler = new UserTaskExportHandler(userTaskWriter, processCache);
  }

  private static Stream<UserTaskIntent> exportableIntents() {
    return TEST_EXPORTABLE_INTENTS.stream();
  }

  @ParameterizedTest(name = "Should be able to export record with intent: {0}")
  @MethodSource("exportableIntents")
  void shouldExportRecord(final UserTaskIntent intent) {
    // given
    final Record<UserTaskRecordValue> record =
        factory.generateRecord(ValueType.USER_TASK, r -> r.withIntent(intent));

    // when - then
    assertThat(handler.canExport(record))
        .as("Handler should be able to export record with intent: %s", intent)
        .isTrue();
  }

  private static Stream<UserTaskIntent> nonExportableIntents() {
    return Stream.of(UserTaskIntent.values())
        .filter(Predicate.not(TEST_EXPORTABLE_INTENTS::contains));
  }

  @ParameterizedTest(name = "Should not export record with unsupported intent: {0}")
  @MethodSource("nonExportableIntents")
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
        // - Add it to `UserTaskExportHandlerTest#TEST_EXPORTABLE_INTENTS` set
        // - Review whether it needs custom handling in `UserTaskExportHandler#export`:
        //   - If so, add a dedicated handling in `UserTaskExportHandler#export`
        //   - Add a dedicated test for the new intent in this class
        .isFalse();
  }

  @Test
  @DisplayName("Should handle user task record with CREATING intent")
  void shouldHandleCreatingUserTaskRecord() {
    // given
    final Record<UserTaskRecordValue> record =
        factory.generateRecord(ValueType.USER_TASK, r -> r.withIntent(UserTaskIntent.CREATING));
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
                  .as("State should be CREATING for record with CREATING intent")
                  .isEqualTo(UserTaskState.CREATING);
              assertThat(model.assignee())
                  .as(
                      "Assignee should be null for CREATING intent, event if it is set in the record")
                  .isNull();
              assertThat(model.completionDate())
                  .as("Completion date should be null for CREATING intent")
                  .isNull();

              assertUserTaskModelFieldsEqualToRecord(model, record);
            });

    // ensure no other methods are called
    verifyNoMoreInteractions(userTaskWriter);
  }

  @ParameterizedTest(name = "Should handle user task transition started record with {0} intent")
  @EnumSource(
      value = UserTaskIntent.class,
      names = {"ASSIGNING", "CLAIMING", "UPDATING", "COMPLETING", "CANCELING"})
  void shouldHandleUserTaskTransitionStartedRecord(final UserTaskIntent intent) {
    // given
    final UserTaskState expectedModelState =
        intent == UserTaskIntent.CLAIMING
            ? UserTaskState.ASSIGNING
            // other intents map to their respective model states directly
            : UserTaskState.valueOf(intent.name());
    final Record<UserTaskRecordValue> record =
        factory.generateRecord(ValueType.USER_TASK, r -> r.withIntent(intent));
    final var recordValue = record.getValue();

    // when
    handler.export(record);

    // then
    verify(userTaskWriter).updateState(eq(recordValue.getUserTaskKey()), eq(expectedModelState));
    verifyNoMoreInteractions(userTaskWriter);
  }

  @ParameterizedTest(
      name = "Should handle task transition ended record with {0} intent mapped to CREATED state")
  @EnumSource(
      value = UserTaskIntent.class,
      names = {"CREATED", "ASSIGNED", "UPDATED"})
  void shouldHandleUserTaskTransitionEndedRecordMappedToCreatedState(final UserTaskIntent intent) {
    // given
    final Record<UserTaskRecordValue> record =
        factory.generateRecord(ValueType.USER_TASK, r -> r.withIntent(intent));
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
                  .as("State should be CREATED for record with %s intent", intent)
                  .isEqualTo(UserTaskState.CREATED);
              assertThat(model.assignee())
                  .as("Assignee should be same as in the record")
                  .isNotNull()
                  .isEqualTo(recordValue.getAssignee());
              assertThat(model.completionDate())
                  .as("Completion date should be null for %s intent", intent)
                  .isNull();
              assertUserTaskModelFieldsEqualToRecord(model, record);
            });

    verifyNoMoreInteractions(userTaskWriter);
  }

  @ParameterizedTest(name = "Should handle user task finalized record with {0} intent")
  @EnumSource(
      value = UserTaskIntent.class,
      names = {"COMPLETED", "CANCELED"})
  void shouldHandleUserTaskFinalizedRecord(final UserTaskIntent intent) {
    // given
    final UserTaskState expectedModelState = UserTaskState.valueOf(intent.name());
    final Record<UserTaskRecordValue> record =
        factory.generateRecord(ValueType.USER_TASK, r -> r.withIntent(intent));
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
                  .as("State should be %s for record with %s intent", expectedModelState, intent)
                  .isEqualTo(expectedModelState);
              assertThat(model.assignee())
                  .as("Assignee should be same as in the record")
                  .isEqualTo(recordValue.getAssignee());
              assertThat(model.completionDate())
                  .as("Completion date should be set for %s intent", intent)
                  .isNotNull()
                  .isEqualTo(DateUtil.toOffsetDateTime(record.getTimestamp()));

              assertUserTaskModelFieldsEqualToRecord(model, record);
            });

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

  @ParameterizedTest(
      name = "Should handle transition denied record with {0} intent by updating state to CREATED")
  @EnumSource(
      value = UserTaskIntent.class,
      names = {"ASSIGNMENT_DENIED", "UPDATE_DENIED", "COMPLETION_DENIED"})
  void shouldHandleUserTaskTransitionDeniedRecord(final UserTaskIntent intent) {
    // given
    final Record<UserTaskRecordValue> record =
        factory.generateRecord(ValueType.USER_TASK, r -> r.withIntent(intent));
    final var recordValue = record.getValue();

    // when
    handler.export(record);

    // then
    verify(userTaskWriter).updateState(eq(recordValue.getUserTaskKey()), eq(UserTaskState.CREATED));
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
    assertThat(model.rootProcessInstanceKey()).isEqualTo(recordValue.getRootProcessInstanceKey());
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
