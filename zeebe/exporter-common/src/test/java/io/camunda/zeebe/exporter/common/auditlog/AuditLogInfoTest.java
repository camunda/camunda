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
import io.camunda.zeebe.exporter.common.auditlog.transformers.AuditLogTransformer.TransformerConfig;
import io.camunda.zeebe.exporter.common.auditlog.transformers.AuditLogTransformerConfigs;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableUserTaskRecordValue;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

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

  /**
   * This test validates that all transformer configs defined in {@link AuditLogTransformerConfigs}
   * have valid mappings in {@link AuditLogInfo}. It ensures that:
   *
   * <ul>
   *   <li>Every ValueType in a config has a valid OperationCategory mapping
   *   <li>Every ValueType in a config has a valid EntityType mapping
   *   <li>Every Intent in a config has a valid OperationType mapping
   * </ul>
   */
  @Test
  void shouldHaveValidMappingsForAllTransformerConfigs() throws Exception {
    final List<String> errors = new ArrayList<>();

    // Get all TransformerConfig constants from AuditLogTransformerConfigs
    final java.lang.reflect.Field[] fields = AuditLogTransformerConfigs.class.getDeclaredFields();

    for (final java.lang.reflect.Field field : fields) {
      if (field.getType().equals(TransformerConfig.class)) {
        field.setAccessible(true);
        final TransformerConfig config = (TransformerConfig) field.get(null);
        final String configName = field.getName();

        // Validate ValueType has OperationCategory mapping
        final AuditLogOperationCategory category =
            getOperationCategoryForValueType(config.valueType());
        if (category == AuditLogOperationCategory.UNKNOWN) {
          errors.add(
              String.format(
                  "[%s] ValueType.%s has no OperationCategory mapping",
                  configName, config.valueType()));
        }

        // Validate ValueType has EntityType mapping
        final AuditLogEntityType entityType = getEntityTypeForValueType(config.valueType());
        if (entityType == AuditLogEntityType.UNKNOWN) {
          errors.add(
              String.format(
                  "[%s] ValueType.%s has no EntityType mapping", configName, config.valueType()));
        }

        // Validate all intents have OperationType mappings
        for (final Intent intent : config.supportedIntents()) {
          final AuditLogOperationType operationType = getOperationTypeForIntent(intent);
          if (operationType == AuditLogOperationType.UNKNOWN) {
            errors.add(
                String.format(
                    "[%s] Intent %s.%s has no OperationType mapping",
                    configName, intent.getClass().getSimpleName(), intent));
          }
        }

        // Validate all rejection intents have OperationType mappings
        for (final Intent intent : config.supportedRejections()) {
          final AuditLogOperationType operationType = getOperationTypeForIntent(intent);
          if (operationType == AuditLogOperationType.UNKNOWN) {
            errors.add(
                String.format(
                    "[%s] Rejection Intent %s.%s has no OperationType mapping",
                    configName, intent.getClass().getSimpleName(), intent));
          }
        }
      }
    }

    assertThat(errors)
        .as("All transformer configs should have valid mappings in AuditLogInfo")
        .isEmpty();
  }

  private AuditLogOperationCategory getOperationCategoryForValueType(final ValueType valueType) {
    final Record<?> record = factory.generateRecord(valueType);
    final AuditLogInfo info = AuditLogInfo.of(record);
    return info.category();
  }

  private AuditLogEntityType getEntityTypeForValueType(final ValueType valueType) {
    final Record<?> record = factory.generateRecord(valueType);
    final AuditLogInfo info = AuditLogInfo.of(record);
    return info.entityType();
  }

  private AuditLogOperationType getOperationTypeForIntent(final Intent intent) {
    // UserTaskIntent.ASSIGNED requires special handling
    if (intent == UserTaskIntent.ASSIGNED) {
      final UserTaskRecordValue value =
          ImmutableUserTaskRecordValue.builder().withAssignee("test-user").build();
      final Record<?> record =
          factory.generateRecord(ValueType.USER_TASK, r -> r.withIntent(intent).withValue(value));
      final AuditLogInfo info = AuditLogInfo.of(record);
      return info.operationType();
    }

    final Record<?> record = factory.generateRecord(ValueType.JOB, r -> r.withIntent(intent));
    final AuditLogInfo info = AuditLogInfo.of(record);
    return info.operationType();
  }
}
