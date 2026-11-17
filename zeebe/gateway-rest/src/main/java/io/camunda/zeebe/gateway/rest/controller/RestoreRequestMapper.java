/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.gateway.protocol.model.RestoreRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RestoreParameters;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Maps the backup selection of a REST restore request onto the cluster configuration request,
 * shared by the per-physical-tenant {@link RecoveryController} and the cluster-wide {@link
 * ClusterRecoveryController}.
 */
@NullMarked
final class RestoreRequestMapper {

  private RestoreRequestMapper() {}

  /** An absent request selects no backups, leaving the choice to the validator of the tenant. */
  static RestoreParameters toRestoreParameters(final @Nullable RestoreRequest restoreRequest) {
    if (restoreRequest == null) {
      return new RestoreParameters(List.of(), null, null);
    }
    return new RestoreParameters(
        restoreRequest.getBackupIds() == null ? List.of() : restoreRequest.getBackupIds(),
        restoreRequest.getFrom(),
        restoreRequest.getTo());
  }
}
