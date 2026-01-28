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
import io.camunda.search.entities.AuditLogEntity.AuditLogEntityType;
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationCategory;
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationType;
import io.camunda.search.entities.AuditLogEntity.AuditLogTenantScope;
import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordMetadataDecoder;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessInstanceModificationRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuditLogEntryTest {

  ProtocolFactory factory = new ProtocolFactory();
  final ImmutableProcessInstanceModificationRecordValue value =
      ImmutableProcessInstanceModificationRecordValue.builder()
          .from(factory.generateObject(ImmutableProcessInstanceModificationRecordValue.class))
          .withTenantId("test-tenant")
          .build();
  private final Record record =
      factory.generateRecord(
          ValueType.PROCESS_INSTANCE_MODIFICATION,
          r ->
              r.withIntent(ProcessInstanceModificationIntent.MODIFIED)
                  .withValue(value)
                  .withBatchOperationReference(123L) // Set to null value to test null case
                  .withAuthorizations(Map.of(Authorization.AUTHORIZED_USERNAME, "test-user")));

  @Test
  void shouldMapFields() {
    // This test verifies the happy path where all fields can be extracted and mapped
    final var entry = AuditLogEntry.of(record);

    // Verify main fields
    assertThat(entry.getEntityKey()).isEqualTo(String.valueOf(record.getKey()));
    assertThat(entry.getEntityType()).isEqualTo(AuditLogEntityType.PROCESS_INSTANCE);
    assertThat(entry.getCategory()).isEqualTo(AuditLogOperationCategory.DEPLOYED_RESOURCES);
    assertThat(entry.getOperationType()).isEqualTo(AuditLogOperationType.MODIFY);

    // Verify nested actor fields
    assertThat(entry.getActor()).isNotNull();
    assertThat(entry.getActor().actorType()).isEqualTo(AuditLogActorType.USER);
    assertThat(entry.getActor().actorId()).isEqualTo("test-user");

    // Verify nested tenant fields
    assertThat(entry.getTenant()).isPresent();
    assertThat(entry.getTenant().get().tenantId()).isEqualTo("test-tenant");
    assertThat(entry.getTenant().get().scope()).isEqualTo(AuditLogTenantScope.TENANT);

    assertThat(entry.getBatchOperationKey()).isEqualTo(123L); // no batch operation in this record
    assertThat(entry.getProcessInstanceKey()).isEqualTo(value.getProcessInstanceKey());
    assertThat(entry.getProcessDefinitionKey()).isEqualTo(value.getProcessDefinitionKey());
    assertThat(entry.getEntityVersion()).isEqualTo(record.getRecordVersion());
    assertThat(entry.getEntityValueType())
        .isEqualTo(ValueType.PROCESS_INSTANCE_MODIFICATION.value());
    assertThat(entry.getEntityOperationIntent())
        .isEqualTo(ProcessInstanceModificationIntent.MODIFIED.value());
    assertThat(entry.getTimestamp())
        .isEqualTo(
            OffsetDateTime.ofInstant(Instant.ofEpochMilli(record.getTimestamp()), ZoneOffset.UTC));

    // Verify fields that are NOT set by AuditLogEntry.of() are null/empty
    assertThat(entry.getBatchOperationType()).isNull();
    assertThat(entry.getResult()).isNull();
    assertThat(entry.getAnnotation()).isNull();
    assertThat(entry.getProcessDefinitionId()).isNull();
    assertThat(entry.getElementInstanceKey()).isNull();
    assertThat(entry.getJobKey()).isNull();
    assertThat(entry.getUserTaskKey()).isNull();
    assertThat(entry.getDecisionEvaluationKey()).isNull();
    assertThat(entry.getDecisionRequirementsId()).isNull();
    assertThat(entry.getDecisionRequirementsKey()).isNull();
    assertThat(entry.getDecisionDefinitionId()).isNull();
    assertThat(entry.getDecisionDefinitionKey()).isNull();
    assertThat(entry.getDeploymentKey()).isNull();
    assertThat(entry.getFormKey()).isNull();
    assertThat(entry.getResourceKey()).isNull();
    assertThat(entry.getRootProcessInstanceKey()).isNull();
  }

  @Test
  void shouldMapNonTenantOwnedRecord() {
    final var record = factory.generateRecord(ValueType.AUTHORIZATION);

    final var entry = AuditLogEntry.of(record);

    assertThat(entry.getTenant()).isEmpty();
  }

  @Test
  void shouldMapNonProcessInstanceRelatedRecord() {
    final var record = factory.generateRecord(ValueType.AUTHORIZATION);

    final var entry = AuditLogEntry.of(record);

    assertThat(entry.getProcessInstanceKey()).isNull();
    assertThat(entry.getProcessDefinitionKey()).isNull();
  }

  @Test
  void shouldMapNonBatchOperationRelatedRecord() {
    final var record =
        factory.generateRecord(
            ValueType.PROCESS_INSTANCE_MODIFICATION,
            r ->
                r.withIntent(ProcessInstanceModificationIntent.MODIFIED)
                    .withBatchOperationReference(
                        RecordMetadataDecoder.batchOperationReferenceNullValue()));

    final var entry = AuditLogEntry.of(record);

    assertThat(entry.getBatchOperationKey()).isNull();
  }
}
