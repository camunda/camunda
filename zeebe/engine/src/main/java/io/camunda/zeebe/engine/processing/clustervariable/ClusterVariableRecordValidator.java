/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.clustervariable;

import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.state.immutable.ClusterVariableState;
import io.camunda.zeebe.protocol.impl.record.value.clustervariable.ClusterVariableRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.util.Either;

public class ClusterVariableRecordValidator {

  private final ClusterVariableState clusterVariableState;

  public ClusterVariableRecordValidator(final ClusterVariableState clusterVariableState) {
    this.clusterVariableState = clusterVariableState;
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

  private boolean tenantScopedVariableExists(final ClusterVariableRecord clusterVariableRecord) {
    return !clusterVariableRecord.getTenantId().isBlank()
        && clusterVariableState.existsAtTenantScope(
            clusterVariableRecord.getNameBuffer(), clusterVariableRecord.getTenantId());
  }

  private boolean globallyScopedVariableExists(final ClusterVariableRecord clusterVariableRecord) {
    return clusterVariableRecord.getTenantId().isBlank()
        && clusterVariableState.existsAtGlobalScope(clusterVariableRecord.getNameBuffer());
  }
}
