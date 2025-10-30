/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers.auditlog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.camunda.exporter.config.ExporterConfiguration.AuditLogConfiguration;
import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.handlers.ExportHandler;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.operation.AuditLogEntity;
import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordMetadataDecoder;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractAuditLogHandler<R extends RecordValue>
    implements ExportHandler<AuditLogEntity, R> {

  private static final Logger LOGGER = LoggerFactory.getLogger(AbstractAuditLogHandler.class);

  protected final ObjectWriter objectWriter;
  private final String indexName;
  private final Set<RejectionType> rejectionTypes;
  private final AuditLogConfiguration auditLogConfiguration;

  protected AbstractAuditLogHandler(
      final String indexName,
      final Set<RejectionType> rejectionTypes,
      final AuditLogConfiguration auditLogConfiguration,
      final ObjectMapper objectMapper) {
    this.indexName = indexName;
    this.rejectionTypes = rejectionTypes;
    this.auditLogConfiguration = auditLogConfiguration;
    objectWriter = objectMapper.writerWithDefaultPrettyPrinter();
  }

  protected abstract boolean handlesIntents(final Record<R> record);

  protected abstract void getOperationSpecificData(
      final R recordValue, final AuditLogEntity entity);

  @Override
  public Class<AuditLogEntity> getEntityType() {
    return AuditLogEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<R> record) {
    final var authorizations = record.getAuthorizations();
    if (handlesIntents(record)
        && (authorizations.containsKey(Authorization.AUTHORIZED_CLIENT_ID)
            || authorizations.containsKey(Authorization.AUTHORIZED_USERNAME))) {
      return true;
    }
    return false;
  }

  @Override
  public List<String> generateIds(final Record<R> record) {
    return List.of(String.valueOf(record.getPosition()));
  }

  @Override
  public AuditLogEntity createNewEntity(final String id) {
    return new AuditLogEntity().setId(id);
  }

  @Override
  public void updateEntity(final Record<R> record, final AuditLogEntity entity) {

    entity
        .setEntityType(record.getValueType().name())
        .setEntityKey(String.valueOf(record.getKey()))
        .setOperationType(record.getIntent().name())
        .setCreateTimestamp(record.getTimestamp());

    getIdentityData(record, entity);

    if (RecordType.COMMAND_REJECTION.equals(record.getRecordType())
        && rejectionTypes.contains(record.getRejectionType())) {
      setRejectionData(record, entity);
      return;
    } else {
      entity.setOperationStatus(OperationStatus.SUCCESS.value);
      getOperationSpecificData(record.getValue(), entity);
    }

    final var batchOperationReference = record.getBatchOperationReference();
    if (batchOperationReference != RecordMetadataDecoder.batchOperationReferenceNullValue()) {
      entity.setBatchOperationKey(String.valueOf(batchOperationReference));
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

  private void setRejectionData(final Record<R> record, final AuditLogEntity entity) {
    final Map<String, Object> rejectionDetails =
        Map.of(
            "rejectionType",
            record.getRejectionType(),
            "rejectionReason",
            record.getRejectionReason());
    entity.setOperationStatus(OperationStatus.FAIL.value);
    entity.setDetails(toJsonDetails(rejectionDetails));
  }

  private void getIdentityData(final Record<R> record, final AuditLogEntity entity) {
    final var authorizations = record.getAuthorizations();
    final var clientId = getClientId(authorizations);
    final var username = getUsername(authorizations);

    final String identityEntityId;
    final EntityType identityEntityType;

    if (clientId.isPresent()) {
      identityEntityId = clientId.get();
      identityEntityType = EntityType.CLIENT;
    } else {
      identityEntityId = username.get();
      identityEntityType = EntityType.USER;
    }

    entity.setIdentityEntityId(identityEntityId).setIdentityEntityType(identityEntityType.name());
  }

  private Optional<String> getUsername(final Map<String, Object> authorizationClaims) {
    return Optional.ofNullable((String) authorizationClaims.get(Authorization.AUTHORIZED_USERNAME));
  }

  private Optional<String> getClientId(final Map<String, Object> authorizationClaims) {
    return Optional.ofNullable(
        (String) authorizationClaims.get(Authorization.AUTHORIZED_CLIENT_ID));
  }

  protected String toJsonDetails(final Object value) {
    try {
      return objectWriter.writeValueAsString(value);
    } catch (final Exception e) {
      LOGGER.error("Failed to parse audit log operation details '{}'", value, e);
      return "";
    }
  }

  public static enum OperationStatus {
    SUCCESS(0),
    FAIL(1);

    private final short value;

    OperationStatus(final int value) {
      this.value = (short) value;
    }
  }
}
