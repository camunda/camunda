/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.clustervariable;

import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.state.immutable.ClusterVariableState;
import io.camunda.zeebe.protocol.impl.record.value.clustervariable.ClusterVariableRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.util.Either;
import org.apache.commons.lang3.StringUtils;

public class ClusterVariableRecordValidator {

  private final ClusterVariableState clusterVariableState;
  private final EngineConfiguration engineConfiguration;

  public ClusterVariableRecordValidator(
      final ClusterVariableState clusterVariableState,
      final EngineConfiguration engineConfiguration) {
    this.clusterVariableState = clusterVariableState;
    this.engineConfiguration = engineConfiguration;
  }

  public Either<Rejection, ClusterVariableRecord> validateName(final ClusterVariableRecord record) {
    final var name = record.getName();
    if (name == null || name.isEmpty()) {
      return Either.left(
          new Rejection(
              RejectionType.INVALID_STATE,
              "Invalid cluster variable name: '%s'. Cluster variable can not be null or empty."
                  .formatted(name)));
    }
    if (name.chars().anyMatch(Character::isWhitespace)) {
      return Either.left(
          new Rejection(
              RejectionType.INVALID_ARGUMENT,
              "Invalid cluster variable name: '%s'. The name must not contains any whitespace."
                  .formatted(name)));
    }
    return Either.right(record);
  }

  public Either<Rejection, ClusterVariableRecord> validateUniqueness(
      final ClusterVariableRecord record) {
    if (globallyScopedVariableExists(record) || tenantScopedVariableExists(record)) {
      return Either.left(
          new Rejection(
              RejectionType.ALREADY_EXISTS,
              "Invalid cluster variable name: '%s'. The name already exists in the scope '%s'"
                  .formatted(
                      record.getName(),
                      record.getTenantId().isBlank()
                          ? "GLOBAL"
                          : "tenant: '%s'".formatted(record.getTenantId()))));
    }
    return Either.right(record);
  }

  public Either<Rejection, ClusterVariableRecord> validateExistence(
      final ClusterVariableRecord record) {
    if (globallyScopedVariableExists(record) || tenantScopedVariableExists(record)) {
      return Either.right(record);
    }
    return Either.left(
        new Rejection(
            RejectionType.NOT_FOUND,
            "Invalid cluster variable name: '%s'. The variable does not exist in the scope '%s'"
                .formatted(
                    record.getName(),
                    record.getTenantId().isBlank()
                        ? "GLOBAL"
                        : "tenant: '%s'".formatted(record.getTenantId()))));
  }

  private boolean tenantScopedVariableExists(final ClusterVariableRecord clusterVariableRecord) {
    return clusterVariableRecord.isTenantScoped()
        && clusterVariableState.existsAtTenantScope(
            clusterVariableRecord.getNameBuffer(), clusterVariableRecord.getTenantId());
  }

  private boolean globallyScopedVariableExists(final ClusterVariableRecord clusterVariableRecord) {
    return clusterVariableRecord.isGloballyScoped()
        && clusterVariableState.existsAtGlobalScope(clusterVariableRecord.getNameBuffer());
  }

  public Either<Rejection, ClusterVariableRecord> ensureValidScope(
      final ClusterVariableRecord clusterVariableRecord) {
    if (clusterVariableRecord.isTenantScoped()
        && StringUtils.isBlank(clusterVariableRecord.getTenantId())) {
      return Either.left(
          new Rejection(
              RejectionType.INVALID_ARGUMENT,
              "Invalid cluster variable scope. Tenant-scoped variables must have a non-blank tenant ID."));
    } else if (clusterVariableRecord.isTenantScoped() || clusterVariableRecord.isGloballyScoped()) {
      return Either.right(clusterVariableRecord);
    } else {
      return Either.left(
          new Rejection(
              RejectionType.INVALID_ARGUMENT,
              "Invalid cluster variable scope. The scope must be either 'GLOBAL' or 'TENANT'."));
    }
  }
}
