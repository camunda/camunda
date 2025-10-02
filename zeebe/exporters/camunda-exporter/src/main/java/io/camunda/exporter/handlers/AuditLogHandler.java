/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.operation.AuditLogEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.MappingRuleIntent;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import io.camunda.zeebe.protocol.record.value.UserRecordValue;
import java.util.List;
import java.util.Set;
import java.util.UUID;

// This should be a "funnel" handler that handles all user audit log related events
// and creates audit log entries accordingly. For this PoC, we only handle Identity-related events
public class AuditLogHandler implements ExportHandler<AuditLogEntity, RecordValue> {

  private static final Set<ValueType> SUPPORTED_VALUE_TYPES =
      Set.of(
          ValueType.GROUP,
          ValueType.ROLE,
          ValueType.USER,
          ValueType.TENANT,
          ValueType.MAPPING_RULE,
          ValueType.AUTHORIZATION);
  private static final Set<Intent> SUPPORTED_INTENTS =
      Set.of(
          GroupIntent.CREATED,
          RoleIntent.CREATED,
          TenantIntent.CREATED,
          UserIntent.CREATED,
          AuthorizationIntent.CREATED,
          MappingRuleIntent.CREATED);
  private final String indexName;

  public AuditLogHandler(final String indexName) {
    this.indexName = indexName;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.NULL_VAL;
  }

  @Override
  public Set<ValueType> getHandledValueTypes() {
    return SUPPORTED_VALUE_TYPES;
  }

  @Override
  public Class<AuditLogEntity> getEntityType() {
    return AuditLogEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<RecordValue> record) {
    return SUPPORTED_VALUE_TYPES.contains(record.getValueType())
        && SUPPORTED_INTENTS.contains(record.getIntent());
  }

  @Override
  public List<String> generateIds(final Record<RecordValue> record) {
    // For simplicity, we generate a random UUID for each audit log entry. In a real-world scenario,
    // we want to use a more meaningful ID generation strategy.
    return List.of(UUID.randomUUID().toString());
  }

  @Override
  public AuditLogEntity createNewEntity(final String id) {
    return new AuditLogEntity().setId(id);
  }

  @Override
  public void updateEntity(final Record<RecordValue> record, final AuditLogEntity entity) {
    // TODO: Extract relevant information from the record to populate audit log fields
    // For now, this is a no-op as we're just creating audit log entries for tracking purposes
    final var value = record.getValue();
    entity
        .setOperationType(record.getIntent().toString())
        .setEntityType(record.getValueType().toString())
        .setUsername("demo")
        .getOperationReference();

    if (value instanceof UserRecordValue) {
      final var userValue = (UserRecordValue) value;
      entity.setAnnotation("User created");
      entity.setEntityId(String.valueOf(userValue.getUserKey()));
    }
  }

  @Override
  public void flush(final AuditLogEntity entity, final BatchRequest batchRequest)
      throws PersistenceException {
    batchRequest.add(indexName, entity);
  }

  @Override
  public String getIndexName() {
    return indexName;
  }
}
