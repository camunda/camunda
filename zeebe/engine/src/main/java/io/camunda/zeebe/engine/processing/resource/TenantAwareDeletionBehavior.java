/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.resource;

import io.camunda.zeebe.engine.processing.identity.AuthenticatedAuthorizedTenants;
import io.camunda.zeebe.engine.processing.identity.AuthorizedTenants;
import io.camunda.zeebe.engine.processing.identity.authorization.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.state.immutable.TenantState;
import io.camunda.zeebe.protocol.impl.record.value.resource.ResourceDeletionRecord;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

final class TenantAwareDeletionBehavior {

  private final AuthorizationCheckBehavior authCheckBehavior;
  private final TenantState tenantState;

  TenantAwareDeletionBehavior(
      final AuthorizationCheckBehavior authCheckBehavior, final TenantState tenantState) {
    this.authCheckBehavior = authCheckBehavior;
    this.tenantState = tenantState;
  }

  boolean untilResourceDeleted(
      final TypedRecord<ResourceDeletionRecord> command, final Function<String, Boolean> callback) {
    final var authorizedTenants = getAuthorizedTenants(command);

    if (AuthorizedTenants.ANONYMOUS.equals(authorizedTenants)) {
      return Optional.of(callback.apply(TenantOwned.DEFAULT_TENANT_IDENTIFIER))
          .filter(Boolean::booleanValue)
          .orElseGet(() -> forEachTenantUntilResourceDeleted(callback));
    }

    for (final var tenant : authorizedTenants.getAuthorizedTenantIds()) {
      if (callback.apply(tenant)) {
        return true;
      }
    }
    return false;
  }

  private AuthorizedTenants getAuthorizedTenants(
      final TypedRecord<ResourceDeletionRecord> command) {
    final String tenantId = command.getValue().getTenantId();
    if (tenantId.isEmpty()) {
      return authCheckBehavior.getAuthorizedTenantIds(command);
    }
    return new AuthenticatedAuthorizedTenants(tenantId);
  }

  private boolean forEachTenantUntilResourceDeleted(final Function<String, Boolean> callback) {
    final var deleted = new AtomicBoolean(false);
    tenantState.forEachTenant(
        tenant -> {
          deleted.set(callback.apply(tenant));
          return !deleted.get();
        });
    return deleted.get();
  }
}
