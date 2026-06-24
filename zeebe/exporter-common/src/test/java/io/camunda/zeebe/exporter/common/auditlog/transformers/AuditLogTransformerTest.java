/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.auditlog.transformers;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.entities.AuditLogEntity.AuditLogOperationResult;
import io.camunda.zeebe.exporter.common.auditlog.AuditLogEntry;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AuditLogTransformerTest {

  private final ProtocolFactory factory = new ProtocolFactory();

  @Test
  void shouldSupportKnownEventIntent() {
    final var config =
        AuditLogTransformer.TransformerConfig.with(ValueType.USER_TASK)
            .withIntents(UserTaskIntent.ASSIGNED);

    final var record =
        factory.generateRecord(
            ValueType.USER_TASK,
            r -> r.withRecordType(RecordType.EVENT).withIntent(UserTaskIntent.ASSIGNED));

    assertThat(config.supports(record)).isTrue();
  }

  @Test
  void shouldNotSupportUnknownEventIntent() {
    final var config =
        AuditLogTransformer.TransformerConfig.with(ValueType.USER_TASK)
            .withIntents(UserTaskIntent.ASSIGNED);

    final var record =
        factory.generateRecord(
            ValueType.USER_TASK,
            r -> r.withRecordType(RecordType.EVENT).withIntent(UserTaskIntent.CANCELED));

    assertThat(config.supports(record)).isFalse();
  }

  @Test
  void shouldSupportMultipleIntents() {
    final var config =
        AuditLogTransformer.TransformerConfig.with(ValueType.USER_TASK)
            .withIntents(UserTaskIntent.CREATED, UserTaskIntent.ASSIGNED);

    final var createdRecord =
        factory.generateRecord(
            ValueType.USER_TASK,
            r -> r.withRecordType(RecordType.EVENT).withIntent(UserTaskIntent.CREATED));

    final var assignedRecord =
        factory.generateRecord(
            ValueType.USER_TASK,
            r -> r.withRecordType(RecordType.EVENT).withIntent(UserTaskIntent.ASSIGNED));

    assertThat(config.supports(createdRecord)).isTrue();
    assertThat(config.supports(assignedRecord)).isTrue();
  }

  @Test
  void shouldSupportCommandRejectionWhenIntentAndRejectionTypeMatch() {
    final var config =
        AuditLogTransformer.TransformerConfig.with(ValueType.USER_TASK)
            .withRejections(UserTaskIntent.CREATED, RejectionType.INVALID_STATE);

    final var record =
        factory.generateRecord(
            ValueType.USER_TASK,
            r ->
                r.withRecordType(RecordType.COMMAND_REJECTION)
                    .withIntent(UserTaskIntent.CREATED)
                    .withRejectionType(RejectionType.INVALID_STATE));

    assertThat(config.supports(record)).isTrue();
  }

  @Test
  void shouldNotSupportCommandRejectionWhenIntentDoesNotMatch() {
    final var config =
        AuditLogTransformer.TransformerConfig.with(ValueType.USER_TASK)
            .withRejections(UserTaskIntent.CREATED, RejectionType.INVALID_STATE);

    final var record =
        factory.generateRecord(
            ValueType.USER_TASK,
            r ->
                r.withRecordType(RecordType.COMMAND_REJECTION)
                    .withIntent(UserTaskIntent.ASSIGNED)
                    .withRejectionType(RejectionType.INVALID_STATE));

    assertThat(config.supports(record)).isFalse();
  }

  @Test
  void shouldNotSupportCommandRejectionWhenRejectionTypeDoesNotMatch() {
    final var config =
        AuditLogTransformer.TransformerConfig.with(ValueType.USER_TASK)
            .withRejections(UserTaskIntent.CREATED, RejectionType.INVALID_STATE);

    final var record =
        factory.generateRecord(
            ValueType.USER_TASK,
            r ->
                r.withRecordType(RecordType.COMMAND_REJECTION)
                    .withIntent(UserTaskIntent.CREATED)
                    .withRejectionType(RejectionType.INVALID_ARGUMENT));

    assertThat(config.supports(record)).isFalse();
  }

  @Test
  void shouldSupportMultipleRejectionTypes() {
    final var config =
        AuditLogTransformer.TransformerConfig.with(ValueType.USER_TASK)
            .withRejections(
                UserTaskIntent.CREATED,
                RejectionType.INVALID_STATE,
                RejectionType.INVALID_ARGUMENT);

    final var invalidStateRecord =
        factory.generateRecord(
            ValueType.USER_TASK,
            r ->
                r.withRecordType(RecordType.COMMAND_REJECTION)
                    .withIntent(UserTaskIntent.CREATED)
                    .withRejectionType(RejectionType.INVALID_STATE));

    final var invalidArgumentRecord =
        factory.generateRecord(
            ValueType.USER_TASK,
            r ->
                r.withRecordType(RecordType.COMMAND_REJECTION)
                    .withIntent(UserTaskIntent.CREATED)
                    .withRejectionType(RejectionType.INVALID_ARGUMENT));

    assertThat(config.supports(invalidStateRecord)).isTrue();
    assertThat(config.supports(invalidArgumentRecord)).isTrue();
  }

  @Test
  void shouldSupportDataCleanupScheduling() {
    final var config =
        AuditLogTransformer.TransformerConfig.with(ValueType.GROUP)
            .withIntents(GroupIntent.DELETED)
            .withDataCleanupIntents(GroupIntent.DELETED);

    final var record =
        factory.generateRecord(
            ValueType.GROUP,
            r -> r.withRecordType(RecordType.EVENT).withIntent(GroupIntent.DELETED));

    assertThat(config.dataCleanupIntents().contains(record.getIntent())).isTrue();
  }

  @Nested
  class CreateTest {
    @Test
    void shouldCreateAuditLogEntryWithSuccess() {
      final var record =
          factory.generateRecord(
              ValueType.PROCESS_INSTANCE_MODIFICATION,
              r -> r.withIntent(ProcessInstanceModificationIntent.MODIFIED));

      final var transformer =
          new AuditLogTransformer<>() {
            @Override
            public TransformerConfig config() {
              return TransformerConfig.with(record.getValueType()).withIntents(record.getIntent());
            }
          };

      final var log = transformer.create(record);

      assertThat(log).isNotNull();
      assertThat(log.getResult()).isEqualTo(AuditLogOperationResult.SUCCESS);
    }

    @Test
    void shouldCreateAuditLogEntryWithRejection() {
      final var record =
          factory.generateRecord(
              ValueType.PROCESS_INSTANCE_MODIFICATION,
              r ->
                  r.withRecordType(RecordType.COMMAND_REJECTION)
                      .withRejectionType(RejectionType.INVALID_STATE)
                      .withIntent(ProcessInstanceModificationIntent.MODIFIED));

      final var transformer =
          new AuditLogTransformer<>() {
            @Override
            public TransformerConfig config() {
              return TransformerConfig.with(record.getValueType())
                  .withRejections(record.getIntent(), record.getRejectionType());
            }
          };

      final var log = transformer.create(record);

      assertThat(log).isNotNull();
      assertThat(log.getResult()).isEqualTo(AuditLogOperationResult.FAIL);
      assertThat(log.getEntityDescription()).isEqualTo(record.getRejectionType().name());
    }

    @Test
    void shouldCreateAuditLogEntryWithCustomTransformerResult() {
      final var record =
          factory.generateRecord(
              ValueType.PROCESS_INSTANCE_MODIFICATION,
              r -> r.withIntent(ProcessInstanceModificationIntent.MODIFIED));

      final var transformer =
          new AuditLogTransformer<>() {
            @Override
            public TransformerConfig config() {
              return TransformerConfig.with(record.getValueType()).withIntents(record.getIntent());
            }

            @Override
            public void transform(
                final Record<io.camunda.zeebe.protocol.record.RecordValue> record,
                final AuditLogEntry log) {
              log.setResult(AuditLogOperationResult.FAIL);
            }
          };

      final var log = transformer.create(record);

      assertThat(log).isNotNull();
      assertThat(log.getResult()).isEqualTo(AuditLogOperationResult.FAIL);
    }
  }
}
