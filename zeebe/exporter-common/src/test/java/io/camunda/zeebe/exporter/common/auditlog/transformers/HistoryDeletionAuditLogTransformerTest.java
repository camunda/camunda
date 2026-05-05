/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.auditlog.transformers;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.entities.AuditLogEntity.AuditLogEntityType;
import io.camunda.zeebe.exporter.common.auditlog.AuditLogEntry;
import io.camunda.zeebe.protocol.impl.record.value.historydeletion.HistoryDeletionRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.HistoryDeletionIntent;
import io.camunda.zeebe.protocol.record.value.HistoryDeletionRecordValue;
import io.camunda.zeebe.protocol.record.value.HistoryDeletionType;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.Test;

class HistoryDeletionAuditLogTransformerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final HistoryDeletionAuditLogTransformer transformer =
      new HistoryDeletionAuditLogTransformer();

  @Test
  void shouldTransformProcessInstanceDeletion() {
    // given
    final HistoryDeletionRecordValue recordValue =
        new HistoryDeletionRecord()
            .setResourceKey(12345L)
            .setResourceType(HistoryDeletionType.PROCESS_INSTANCE)
            .setProcessId("myProcess")
            .setTenantId("tenant-1");

    final Record<HistoryDeletionRecordValue> record =
        factory.generateRecord(
            ValueType.HISTORY_DELETION,
            r -> r.withIntent(HistoryDeletionIntent.DELETED).withValue(recordValue));

    // when
    final var entity = AuditLogEntry.of(record);
    transformer.transform(record, entity);

    // then
    assertThat(entity.getEntityKey()).isEqualTo("12345");
    assertThat(entity.getEntityType()).isEqualTo(AuditLogEntityType.PROCESS_INSTANCE);
    assertThat(entity.getEntityDescription()).isEqualTo("PROCESS_INSTANCE");
    assertThat(entity.getProcessDefinitionId()).isEqualTo("myProcess");
    assertThat(entity.getDecisionDefinitionId()).isNull();
  }

  @Test
  void shouldTransformProcessDefinitionDeletion() {
    // given
    final HistoryDeletionRecordValue recordValue =
        new HistoryDeletionRecord()
            .setResourceKey(54321L)
            .setResourceType(HistoryDeletionType.PROCESS_DEFINITION)
            .setProcessId("myProcessDef")
            .setTenantId("tenant-2");

    final Record<HistoryDeletionRecordValue> record =
        factory.generateRecord(
            ValueType.HISTORY_DELETION,
            r -> r.withIntent(HistoryDeletionIntent.DELETED).withValue(recordValue));

    // when
    final var entity = AuditLogEntry.of(record);
    transformer.transform(record, entity);

    // then
    assertThat(entity.getEntityKey()).isEqualTo("54321");
    assertThat(entity.getEntityType()).isEqualTo(AuditLogEntityType.RESOURCE);
    assertThat(entity.getEntityDescription()).isEqualTo("PROCESS_DEFINITION");
    assertThat(entity.getProcessDefinitionId()).isEqualTo("myProcessDef");
    assertThat(entity.getDecisionDefinitionId()).isNull();
  }

  @Test
  void shouldTransformDecisionInstanceDeletion() {
    // given
    final HistoryDeletionRecordValue recordValue =
        new HistoryDeletionRecord()
            .setResourceKey(98765L)
            .setResourceType(HistoryDeletionType.DECISION_INSTANCE)
            .setDecisionDefinitionId("myDecision")
            .setTenantId("tenant-3");

    final Record<HistoryDeletionRecordValue> record =
        factory.generateRecord(
            ValueType.HISTORY_DELETION,
            r -> r.withIntent(HistoryDeletionIntent.DELETED).withValue(recordValue));

    // when
    final var entity = AuditLogEntry.of(record);
    transformer.transform(record, entity);

    // then
    assertThat(entity.getEntityKey()).isEqualTo("98765");
    assertThat(entity.getEntityType()).isEqualTo(AuditLogEntityType.DECISION);
    assertThat(entity.getEntityDescription()).isEqualTo("DECISION_INSTANCE");
    assertThat(entity.getDecisionDefinitionId()).isEqualTo("myDecision");
    assertThat(entity.getProcessDefinitionId()).isNull();
  }

  @Test
  void shouldTransformDecisionRequirementsDeletion() {
    // given
    final HistoryDeletionRecordValue recordValue =
        new HistoryDeletionRecord()
            .setResourceKey(11111L)
            .setResourceType(HistoryDeletionType.DECISION_REQUIREMENTS)
            .setDecisionDefinitionId("myDRD")
            .setTenantId("tenant-4");

    final Record<HistoryDeletionRecordValue> record =
        factory.generateRecord(
            ValueType.HISTORY_DELETION,
            r -> r.withIntent(HistoryDeletionIntent.DELETED).withValue(recordValue));

    // when
    final var entity = AuditLogEntry.of(record);
    transformer.transform(record, entity);

    // then
    assertThat(entity.getEntityKey()).isEqualTo("11111");
    assertThat(entity.getEntityType()).isEqualTo(AuditLogEntityType.RESOURCE);
    assertThat(entity.getEntityDescription()).isEqualTo("DECISION_REQUIREMENTS");
    assertThat(entity.getDecisionDefinitionId()).isEqualTo("myDRD");
    assertThat(entity.getProcessDefinitionId()).isNull();
  }

  @Test
  void shouldHandleEmptyProcessIdAndDecisionDefinitionId() {
    // given - batch deletions don't set processId or decisionDefinitionId
    final HistoryDeletionRecordValue recordValue =
        new HistoryDeletionRecord()
            .setResourceKey(22222L)
            .setResourceType(HistoryDeletionType.PROCESS_INSTANCE)
            .setProcessId("")
            .setDecisionDefinitionId("")
            .setTenantId("tenant-5");

    final Record<HistoryDeletionRecordValue> record =
        factory.generateRecord(
            ValueType.HISTORY_DELETION,
            r -> r.withIntent(HistoryDeletionIntent.DELETED).withValue(recordValue));

    // when
    final var entity = AuditLogEntry.of(record);
    transformer.transform(record, entity);

    // then
    assertThat(entity.getEntityKey()).isEqualTo("22222");
    assertThat(entity.getEntityType()).isEqualTo(AuditLogEntityType.PROCESS_INSTANCE);
    assertThat(entity.getProcessDefinitionId()).isNull();
    assertThat(entity.getDecisionDefinitionId()).isNull();
  }

  @Test
  void shouldScheduleCleanUp() {
    // given
    final Record<HistoryDeletionRecordValue> record =
        factory.generateRecord(
            ValueType.HISTORY_DELETION, r -> r.withIntent(HistoryDeletionIntent.DELETED));

    // then
    assertThat(transformer.triggersCleanUp(record)).isTrue();
  }

  @Test
  void shouldSupportDeletedIntent() {
    // given
    final Record<HistoryDeletionRecordValue> record =
        factory.generateRecord(
            ValueType.HISTORY_DELETION, r -> r.withIntent(HistoryDeletionIntent.DELETED));

    // then
    assertThat(transformer.supports(record)).isTrue();
  }

  @Test
  void shouldNotSupportDeleteIntent() {
    // given
    final Record<HistoryDeletionRecordValue> record =
        factory.generateRecord(
            ValueType.HISTORY_DELETION, r -> r.withIntent(HistoryDeletionIntent.DELETE));

    // then
    assertThat(transformer.supports(record)).isFalse();
  }
}
