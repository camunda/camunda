/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers.waitstate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.db.rdbms.write.domain.WaitStateDbModel;
import io.camunda.db.rdbms.write.service.WaitStateWriter;
import io.camunda.zeebe.exporter.common.waitstate.WaitStateEntry.WaitStateType;
import io.camunda.zeebe.exporter.common.waitstate.transformers.JobBasedWaitStateTransformer;
import io.camunda.zeebe.exporter.common.waitstate.transformers.UserTaskBasedWaitStateTransformer;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ImmutableJobRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableUserTaskRecordValue;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.protocol.record.value.JobListenerEventType;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WaitStateAddUpdateHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Mock private WaitStateWriter waitStateWriter;
  @Captor private ArgumentCaptor<WaitStateDbModel> modelCaptor;

  private WaitStateAddUpdateHandler<JobRecordValue> handler;

  @BeforeEach
  void setUp() {
    handler =
        new WaitStateAddUpdateHandler<>(
            waitStateWriter, new JobBasedWaitStateTransformer(), objectMapper);
  }

  @Test
  void shouldAcceptJobMigratedRecord() {
    // given
    final Record<JobRecordValue> record =
        factory.generateRecord(
            ValueType.JOB, r -> r.withRecordType(RecordType.EVENT).withIntent(JobIntent.MIGRATED));

    // when / then
    assertThat(handler.canExport(record)).isTrue();
  }

  @Test
  void shouldUpdateWaitStateRowOnMigratedExport() {
    // given
    final JobRecordValue value =
        ImmutableJobRecordValue.builder()
            .from(factory.generateObject(JobRecordValue.class))
            .withType("payment-service")
            .withJobKind(JobKind.BPMN_ELEMENT)
            .withJobListenerEventType(JobListenerEventType.UNSPECIFIED)
            .withRetries(3)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .withElementId("task-after-migration")
            .withElementInstanceKey(300L)
            .withProcessInstanceKey(200L)
            .withRootProcessInstanceKey(100L)
            .withTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
            .build();
    final Record<JobRecordValue> record =
        factory.generateRecord(
            ValueType.JOB,
            r ->
                r.withKey(999L)
                    .withRecordType(RecordType.EVENT)
                    .withIntent(JobIntent.MIGRATED)
                    .withValue(value));

    // when
    handler.export(record);

    // then — update (not insert) is called with the post-migration elementId
    verify(waitStateWriter).update(modelCaptor.capture());
    final WaitStateDbModel model = modelCaptor.getValue();
    assertThat(model.waitStateKey()).isEqualTo(999L);
    assertThat(model.elementId()).isEqualTo("task-after-migration");
    assertThat(model.elementType()).isEqualTo(BpmnElementType.SERVICE_TASK.name());
  }

  @Test
  void shouldExportJobCreatedRecord() {
    // given
    final Record<JobRecordValue> record =
        factory.generateRecord(
            ValueType.JOB, r -> r.withRecordType(RecordType.EVENT).withIntent(JobIntent.CREATED));

    // when / then
    assertThat(handler.canExport(record)).isTrue();
  }

  @Test
  void shouldNotExportJobCompletedRecord() {
    // given
    final Record<JobRecordValue> record =
        factory.generateRecord(
            ValueType.JOB, r -> r.withRecordType(RecordType.EVENT).withIntent(JobIntent.COMPLETED));

    // when / then
    assertThat(handler.canExport(record)).isFalse();
  }

  @Test
  void shouldInsertWaitStateRowOnExport() {
    // given
    final JobRecordValue value =
        ImmutableJobRecordValue.builder()
            .from(factory.generateObject(JobRecordValue.class))
            .withType("payment-service")
            .withJobKind(JobKind.BPMN_ELEMENT)
            .withJobListenerEventType(JobListenerEventType.UNSPECIFIED)
            .withRetries(3)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .withElementId("task-payment")
            .withElementInstanceKey(300L)
            .withProcessInstanceKey(200L)
            .withRootProcessInstanceKey(100L)
            .withBpmnProcessId("payment-process")
            .withTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
            .build();
    final Record<JobRecordValue> record =
        factory.generateRecord(
            ValueType.JOB,
            r ->
                r.withKey(999L)
                    .withRecordType(RecordType.EVENT)
                    .withIntent(JobIntent.CREATED)
                    .withValue(value));

    // when
    handler.export(record);

    // then
    verify(waitStateWriter).create(modelCaptor.capture());
    final WaitStateDbModel model = modelCaptor.getValue();
    assertThat(model.waitStateKey()).isEqualTo(999L);
    assertThat(model.rootProcessInstanceKey()).isEqualTo(100L);
    assertThat(model.processInstanceKey()).isEqualTo(200L);
    assertThat(model.elementInstanceKey()).isEqualTo(300L);
    assertThat(model.elementId()).isEqualTo("task-payment");
    assertThat(model.elementType()).isEqualTo(BpmnElementType.SERVICE_TASK.name());
    assertThat(model.waitStateType()).isEqualTo(WaitStateType.JOB.name());
    assertThat(model.processDefinitionId()).isEqualTo("payment-process");
    assertThat(model.tenantId()).isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    assertThat(model.partitionId()).isEqualTo(record.getPartitionId());
    assertThat(model.details())
        .contains("\"jobType\":\"payment-service\"")
        .contains("\"jobKind\":\"BPMN_ELEMENT\"")
        .contains("\"retries\":3");
  }

  @Test
  void shouldPreserveElementIdOnJobFailedExport() {
    // given — FAILED records may carry NO_CATCH_EVENT_FOUND as elementId; the model
    // must have null elementId so the SQL conditional skips overwriting ELEMENT_ID.
    final JobRecordValue value =
        ImmutableJobRecordValue.builder()
            .from(factory.generateObject(JobRecordValue.class))
            .withType("payment-service")
            .withJobKind(JobKind.BPMN_ELEMENT)
            .withJobListenerEventType(JobListenerEventType.UNSPECIFIED)
            .withRetries(0)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .withElementId("NO_CATCH_EVENT_FOUND")
            .withElementInstanceKey(300L)
            .withProcessInstanceKey(200L)
            .withRootProcessInstanceKey(100L)
            .withTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
            .build();
    final Record<JobRecordValue> record =
        factory.generateRecord(
            ValueType.JOB,
            r ->
                r.withKey(999L)
                    .withRecordType(RecordType.EVENT)
                    .withIntent(JobIntent.FAILED)
                    .withValue(value));

    // when
    handler.export(record);

    // then — update is called, and elementId is null so SQL preserves the stored value
    verify(waitStateWriter).update(modelCaptor.capture());
    final WaitStateDbModel model = modelCaptor.getValue();
    assertThat(model.elementId()).isNull();
    assertThat(model.details()).contains("\"retries\":0");
  }

  @Test
  void shouldInsertWaitStateRowOnUserTaskCreated() {
    // given
    final WaitStateAddUpdateHandler<UserTaskRecordValue> userTaskHandler =
        new WaitStateAddUpdateHandler<>(
            waitStateWriter, new UserTaskBasedWaitStateTransformer(), objectMapper);
    final Record<UserTaskRecordValue> record =
        userTaskRecord(UserTaskIntent.CREATED, "approve-task");

    // when
    userTaskHandler.export(record);

    // then
    verify(waitStateWriter).create(modelCaptor.capture());
    final WaitStateDbModel model = modelCaptor.getValue();
    assertThat(model.waitStateKey()).isEqualTo(999L);
    assertThat(model.elementId()).isEqualTo("approve-task");
    assertThat(model.elementType()).isEqualTo(BpmnElementType.USER_TASK.name());
    assertThat(model.waitStateType()).isEqualTo(WaitStateType.USER_TASK.name());
    assertThat(model.details())
        .contains("\"taskKey\":999")
        .contains("\"dueDate\":\"2026-10-13T10:00:00+01:00\"");
  }

  @Test
  void shouldUpdateWaitStateRowOnUserTaskUpdated() {
    // given
    final WaitStateAddUpdateHandler<UserTaskRecordValue> userTaskHandler =
        new WaitStateAddUpdateHandler<>(
            waitStateWriter, new UserTaskBasedWaitStateTransformer(), objectMapper);
    final Record<UserTaskRecordValue> record =
        userTaskRecord(UserTaskIntent.UPDATED, "approve-task");

    // when
    userTaskHandler.export(record);

    // then — UPDATED is an update intent, so update (not insert) is called
    verify(waitStateWriter).update(modelCaptor.capture());
    final WaitStateDbModel model = modelCaptor.getValue();
    assertThat(model.waitStateKey()).isEqualTo(999L);
    assertThat(model.elementId()).isEqualTo("approve-task");
    assertThat(model.waitStateType()).isEqualTo(WaitStateType.USER_TASK.name());
  }

  private Record<UserTaskRecordValue> userTaskRecord(
      final UserTaskIntent intent, final String elementId) {
    final UserTaskRecordValue value =
        ImmutableUserTaskRecordValue.builder()
            .from(factory.generateObject(UserTaskRecordValue.class))
            .withUserTaskKey(999L)
            .withDueDate("2026-10-13T10:00:00+01:00")
            .withElementId(elementId)
            .withElementInstanceKey(300L)
            .withProcessInstanceKey(200L)
            .withRootProcessInstanceKey(100L)
            .withTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
            .build();
    return factory.generateRecord(
        ValueType.USER_TASK,
        r -> r.withKey(999L).withRecordType(RecordType.EVENT).withIntent(intent).withValue(value));
  }
}
