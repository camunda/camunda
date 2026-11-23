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
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationCategory;
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationResult;
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationType;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessDefinitionSuspensionIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessDefinitionSuspensionRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessDefinitionSuspensionResolvedVersionValue;
import io.camunda.zeebe.protocol.record.value.ProcessDefinitionSuspensionOutcome;
import io.camunda.zeebe.protocol.record.value.ProcessDefinitionSuspensionRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessDefinitionSuspensionResolvedVersionValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ProcessDefinitionSuspensionAuditLogTransformerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final ProcessDefinitionSuspensionAuditLogTransformer transformer =
      new ProcessDefinitionSuspensionAuditLogTransformer();

  @ParameterizedTest
  @EnumSource(
      value = ProcessDefinitionSuspensionIntent.class,
      names = {"SUSPENDED", "RESUMED"})
  void shouldNotAuditSummaryEventAsVersionsAreAuditedByTheirProcessEvent(
      final ProcessDefinitionSuspensionIntent intent) {
    // given
    final Record<ProcessDefinitionSuspensionRecordValue> record =
        factory.generateRecord(
            ValueType.PROCESS_DEFINITION_SUSPENSION,
            r ->
                r.withRecordType(RecordType.EVENT)
                    .withIntent(intent)
                    .withValue(
                        suspensionValue(
                            -1L,
                            "process-id",
                            List.of(
                                version(11L, ProcessDefinitionSuspensionOutcome.SUSPENDED),
                                version(12L, ProcessDefinitionSuspensionOutcome.RESUMED)))));

    // when / then
    assertThat(transformer.supports(record)).isFalse();
  }

  @Test
  void shouldTransformRejectedSuspendAddressedByKeyAsFailed() {
    // given
    final var record =
        suspensionRejection(
            ProcessDefinitionSuspensionIntent.SUSPEND,
            RejectionType.INVALID_STATE,
            suspensionValue(123L, "", List.of()));

    // when
    final var entity = transformer.create(record);

    // then
    assertThat(transformer.supports(record)).isTrue();
    assertThat(entity.getOperationType()).isEqualTo(AuditLogOperationType.SUSPEND);
    assertThat(entity.getResult()).isEqualTo(AuditLogOperationResult.FAIL);
    assertThat(entity.getCategory()).isEqualTo(AuditLogOperationCategory.DEPLOYED_RESOURCES);
    assertThat(entity.getEntityType()).isEqualTo(AuditLogEntityType.RESOURCE);
    assertThat(entity.getProcessDefinitionId()).isNull();
    assertThat(entity.getProcessDefinitionKey()).isEqualTo(123L);
    assertThat(entity.getEntityKey()).isEqualTo("123");
    assertThat(entity.getTenant().orElseThrow().tenantId()).isEqualTo("tenant-1");
  }

  @Test
  void shouldTransformRejectedResumeAddressedByIdAsFailed() {
    // given
    final var record =
        suspensionRejection(
            ProcessDefinitionSuspensionIntent.RESUME,
            RejectionType.INVALID_STATE,
            suspensionValue(-1L, "process-id", List.of()));

    // when
    final var entity = transformer.create(record);

    // then
    assertThat(transformer.supports(record)).isTrue();
    assertThat(entity.getOperationType()).isEqualTo(AuditLogOperationType.RESUME);
    assertThat(entity.getResult()).isEqualTo(AuditLogOperationResult.FAIL);
    assertThat(entity.getProcessDefinitionId()).isEqualTo("process-id");
    assertThat(entity.getProcessDefinitionKey()).isNull();
    assertThat(entity.getEntityKey()).isEqualTo("process-id");
    assertThat(entity.getEntityDescription()).isEqualTo(RejectionType.INVALID_STATE.name());
    assertThat(entity.getTenant().orElseThrow().tenantId()).isEqualTo("tenant-1");
  }

  @ParameterizedTest
  @EnumSource(RejectionType.class)
  void shouldAuditOnlyRejectionsTheUserCanCause(final RejectionType rejectionType) {
    // given
    final var record =
        suspensionRejection(
            ProcessDefinitionSuspensionIntent.SUSPEND,
            rejectionType,
            suspensionValue(123L, "", List.of()));

    // when / then
    assertThat(transformer.supports(record))
        .isEqualTo(rejectionType == RejectionType.INVALID_STATE);
  }

  private Record<ProcessDefinitionSuspensionRecordValue> suspensionRejection(
      final ProcessDefinitionSuspensionIntent intent,
      final RejectionType rejectionType,
      final ProcessDefinitionSuspensionRecordValue value) {
    return factory.generateRecord(
        ValueType.PROCESS_DEFINITION_SUSPENSION,
        r ->
            r.withRecordType(RecordType.COMMAND_REJECTION)
                .withRejectionType(rejectionType)
                .withIntent(intent)
                .withValue(value));
  }

  private ProcessDefinitionSuspensionRecordValue suspensionValue(
      final long processDefinitionKey,
      final String bpmnProcessId,
      final List<ProcessDefinitionSuspensionResolvedVersionValue> resolvedVersions) {
    return ImmutableProcessDefinitionSuspensionRecordValue.builder()
        .from(factory.generateObject(ProcessDefinitionSuspensionRecordValue.class))
        .withProcessDefinitionKey(processDefinitionKey)
        .withBpmnProcessId(bpmnProcessId)
        .withVersion(-1)
        .withTenantId("tenant-1")
        .withResolvedVersions(resolvedVersions)
        .build();
  }

  private ProcessDefinitionSuspensionResolvedVersionValue version(
      final long processDefinitionKey, final ProcessDefinitionSuspensionOutcome outcome) {
    return ImmutableProcessDefinitionSuspensionResolvedVersionValue.builder()
        .withProcessDefinitionKey(processDefinitionKey)
        .withVersion(1)
        .withOutcome(outcome)
        .build();
  }
}
