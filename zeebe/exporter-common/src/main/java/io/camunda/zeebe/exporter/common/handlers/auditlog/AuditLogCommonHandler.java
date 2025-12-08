/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.handlers.auditlog;

import io.camunda.webapps.schema.entities.auditlog.AuditLogActorType;
import io.camunda.webapps.schema.entities.auditlog.AuditLogEntityType;
import io.camunda.webapps.schema.entities.auditlog.AuditLogOperationCategory;
import io.camunda.webapps.schema.entities.auditlog.AuditLogOperationType;
import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordMetadataDecoder;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common handler logic for audit log processing shared between different exporter implementations.
 */
public class AuditLogCommonHandler {

  private static final Logger LOG = LoggerFactory.getLogger(AuditLogCommonHandler.class);

  /**
   * Maps a record {@link ValueType} to the corresponding {@link AuditLogEntityType}.
   *
   * @param valueType the value type from the record
   * @return the corresponding audit log entity type
   */
  public static AuditLogEntityType getEntityType(final ValueType valueType) {
    return switch (valueType) {
      case PROCESS_INSTANCE,
          PROCESS_INSTANCE_CREATION,
          PROCESS_INSTANCE_MODIFICATION,
          PROCESS_INSTANCE_MIGRATION ->
          AuditLogEntityType.PROCESS_INSTANCE;
      case INCIDENT -> AuditLogEntityType.INCIDENT;
      case VARIABLE -> AuditLogEntityType.VARIABLE;
      case DECISION_EVALUATION -> AuditLogEntityType.DECISION;
      case BATCH_OPERATION_CREATION, BATCH_OPERATION_LIFECYCLE_MANAGEMENT ->
          AuditLogEntityType.BATCH;
      case USER -> AuditLogEntityType.USER;
      case MAPPING_RULE -> AuditLogEntityType.MAPPING_RULE;
      case AUTHORIZATION -> AuditLogEntityType.AUTHORIZATION;
      case GROUP -> AuditLogEntityType.GROUP;
      case ROLE -> AuditLogEntityType.ROLE;
      case TENANT -> AuditLogEntityType.TENANT;
      case USER_TASK -> AuditLogEntityType.USER_TASK;
      default -> AuditLogEntityType.UNKNOWN;
    };
  }

  /**
   * Maps a record {@link Intent} to the corresponding {@link AuditLogOperationType}.
   *
   * @param intent the intent from the record
   * @return the corresponding audit log operation type
   */
  public static AuditLogOperationType getOperationType(final Intent intent) {
    return switch (intent) {
      case ProcessInstanceModificationIntent.MODIFIED -> AuditLogOperationType.MODIFY;
      // TODO: map additional intents to operations here
      default -> AuditLogOperationType.UNKNOWN;
    };
  }

  /**
   * Maps a record {@link ValueType} to the corresponding {@link AuditLogOperationCategory}.
   *
   * @param valueType the value type from the record
   * @return the corresponding audit log operation category
   */
  public static AuditLogOperationCategory getOperationCategory(final ValueType valueType) {
    return switch (valueType) {
      case PROCESS_INSTANCE,
          PROCESS_INSTANCE_CREATION,
          PROCESS_INSTANCE_MODIFICATION,
          PROCESS_INSTANCE_MIGRATION,
          INCIDENT,
          VARIABLE,
          DECISION_EVALUATION,
          BATCH_OPERATION_CREATION,
          BATCH_OPERATION_LIFECYCLE_MANAGEMENT ->
          AuditLogOperationCategory.OPERATOR;
      case USER, MAPPING_RULE, AUTHORIZATION, GROUP, ROLE, TENANT ->
          AuditLogOperationCategory.ADMIN;
      case USER_TASK -> AuditLogOperationCategory.USER_TASK;
      default -> AuditLogOperationCategory.UNKNOWN;
    };
  }

  /**
   * Checks if the record contains actor data (either client ID or username).
   *
   * @param record the record to check
   * @return true if actor data is present, false otherwise
   */
  public static boolean hasActorData(final Record<? extends RecordValue> record) {
    final var authorizations = record.getAuthorizations();
    return getClientId(authorizations).isPresent() || getUsername(authorizations).isPresent();
  }

  /**
   * Extracts the username from authorization claims.
   *
   * @param authorizationClaims the authorization claims map
   * @return an Optional containing the username if present
   */
  public static Optional<String> getUsername(final Map<String, Object> authorizationClaims) {
    return Optional.ofNullable((String) authorizationClaims.get(Authorization.AUTHORIZED_USERNAME));
  }

  /**
   * Extracts the client ID from authorization claims.
   *
   * @param authorizationClaims the authorization claims map
   * @return an Optional containing the client ID if present
   */
  public static Optional<String> getClientId(final Map<String, Object> authorizationClaims) {
    return Optional.ofNullable(
        (String) authorizationClaims.get(Authorization.AUTHORIZED_CLIENT_ID));
  }

  /**
   * Extracts actor information from a record.
   *
   * @param record the record to extract actor data from
   * @return an ActorData object containing actor ID and type
   */
  public static ActorData extractActorData(final Record<? extends RecordValue> record) {
    final var authorizations = record.getAuthorizations();
    final var clientId = getClientId(authorizations);
    final var username = getUsername(authorizations);

    final String actorId;
    final AuditLogActorType actorType;
    if (clientId.isPresent()) {
      actorId = clientId.get();
      actorType = AuditLogActorType.CLIENT;
    } else if (username.isPresent()) {
      actorId = username.get();
      actorType = AuditLogActorType.USER;
    } else {
      LOG.error(
          "No actor information found in record: key {}, intent {}",
          record.getKey(),
          record.getIntent().name());
      actorId = "unknown";
      actorType = AuditLogActorType.UNKNOWN;
    }

    return new ActorData(actorId, actorType);
  }

  /**
   * Extracts the batch operation key from a record if present.
   *
   * @param record the record to extract batch operation data from
   * @return an Optional containing the batch operation key if present
   */
  public static Optional<Long> extractBatchOperationKey(
      final Record<? extends RecordValue> record) {
    final var batchOperationKey = record.getBatchOperationReference();
    if (RecordMetadataDecoder.batchOperationReferenceNullValue() != batchOperationKey) {
      return Optional.of(batchOperationKey);
    }
    return Optional.empty();
  }

  /** A simple data class to hold actor information. */
  public record ActorData(String actorId, AuditLogActorType actorType) {}
}
