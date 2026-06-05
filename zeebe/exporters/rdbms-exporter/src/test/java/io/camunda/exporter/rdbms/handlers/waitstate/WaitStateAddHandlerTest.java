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
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ImmutableJobRecordValue;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.protocol.record.value.JobListenerEventType;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WaitStateAddHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Mock private WaitStateWriter waitStateWriter;
  @Captor private ArgumentCaptor<WaitStateDbModel> modelCaptor;

  private WaitStateAddHandler<JobRecordValue> handler;

  @BeforeEach
  void setUp() {
    handler =
        new WaitStateAddHandler<>(
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
    assertThat(model.tenantId()).isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    assertThat(model.partitionId()).isEqualTo(record.getPartitionId());
    assertThat(model.details())
        .contains("\"jobType\":\"payment-service\"")
        .contains("\"jobKind\":\"BPMN_ELEMENT\"")
        .contains("\"retries\":3");
  }
}
