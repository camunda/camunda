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
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationType;
import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.exporter.common.auditlog.AuditLogConfiguration;
import io.camunda.zeebe.exporter.common.auditlog.AuditLogEntry;
import io.camunda.zeebe.exporter.common.auditlog.AuditLogInfo;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableSourceRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableVariableRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableOperationType;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class VariableAddUpdateAuditLogTransformerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final VariableAddUpdateAuditLogTransformer transformer =
      new VariableAddUpdateAuditLogTransformer();

  @Test
  void shouldSupportVariableRecordWithApiSource() {
    // given
    final var variableSource = new VariableSourceRecord().setType(VariableOperationType.API);
    final var record = variableRecord(VariableIntent.CREATED, variableSource);

    // then
    assertThat(transformer.supports(record)).isTrue();
  }

  @ParameterizedTest
  @CsvSource({
    "API, CREATED, CREATE",
    "API, UPDATED, UPDATE",
    "USER_TASK_COMPLETION, CREATED, CREATE",
    "USER_TASK_COMPLETION, UPDATED, UPDATE"
  })
  void shouldClassifyAndFilterVariableChangesAsDeployedResources(
      final VariableOperationType source,
      final VariableIntent intent,
      final AuditLogOperationType operation) {
    // given
    final var record = variableRecord(intent, new VariableSourceRecord().setType(source));
    final var config = new AuditLogConfiguration();

    // when
    final var info = AuditLogInfo.of(record);
    final var entry = AuditLogEntry.of(record);
    transformer.transform(record, entry);

    // then
    assertThat(transformer.supports(record)).isTrue();
    assertThat(entry.getCategory()).isEqualTo(AuditLogOperationCategory.DEPLOYED_RESOURCES);
    assertThat(entry.getEntityType()).isEqualTo(AuditLogEntityType.VARIABLE);
    assertThat(entry.getOperationType()).isEqualTo(operation);

    config.getUser().setCategories(Set.of(AuditLogOperationCategory.USER_TASKS));
    assertThat(config.isEnabled(info)).isFalse();
    config.getUser().setCategories(Set.of(AuditLogOperationCategory.DEPLOYED_RESOURCES));
    assertThat(config.isEnabled(info)).isTrue();
    config.getUser().setExcludes(Set.of(AuditLogEntityType.VARIABLE));
    assertThat(config.isEnabled(info)).isFalse();
  }

  @Test
  void shouldNotSupportVariableRecordWithUnknownSource() {
    // given
    final var record =
        variableRecord(
            VariableIntent.CREATED,
            new VariableSourceRecord().setType(VariableOperationType.UNKNOWN));

    // then
    assertThat(transformer.supports(record)).isFalse();
  }

  @Test
  void shouldTransformVariableRecord() {
    // given
    final Record<VariableRecordValue> record =
        variableRecord(VariableIntent.CREATED, VariableSourceRecord.api());

    // when
    final var entity = AuditLogEntry.of(record);
    transformer.transform(record, entity);

    // then
    assertThat(entity.getProcessDefinitionKey()).isEqualTo(456L);
    assertThat(entity.getProcessDefinitionId()).isEqualTo("bpmn-process-id");
    assertThat(entity.getProcessInstanceKey()).isEqualTo(123L);
    assertThat(entity.getElementInstanceKey()).isEqualTo(789L);
    assertThat(entity.getOperationType()).isEqualTo(AuditLogOperationType.CREATE);
    assertThat(entity.getTenant().get().tenantId()).isEqualTo("tenant-1");
    assertThat(entity.getRootProcessInstanceKey())
        .isPositive()
        .isEqualTo(record.getValue().getRootProcessInstanceKey());
    assertThat(entity.getEntityDescription()).isEqualTo("variable-name");
  }

  private Record<VariableRecordValue> variableRecord(
      final VariableIntent intent, final VariableSourceRecord variableSource) {
    final VariableRecordValue recordValue =
        ImmutableVariableRecordValue.builder()
            .from(factory.generateObject(VariableRecordValue.class))
            .withName("variable-name")
            .withProcessDefinitionKey(456L)
            .withBpmnProcessId("bpmn-process-id")
            .withTenantId("tenant-1")
            .withProcessInstanceKey(123L)
            .withScopeKey(789L)
            .withSource(variableSource)
            .build();

    return factory.generateRecord(
        ValueType.VARIABLE,
        r ->
            r.withIntent(intent)
                .withValue(recordValue)
                .withAuthorizations(Map.of(Authorization.AUTHORIZED_USERNAME, "test-user")));
  }
}
