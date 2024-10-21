/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.tenant;

import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.TenantState;
import io.camunda.zeebe.engine.state.immutable.UserState;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

public class TenantRemoveEntityProcessor extends AbstractTenantProcessor {

  private static final String TENANT_NOT_FOUND_ERROR =
      "Expected to update entity to tenant with key '%s', but no tenant with this key exists.";

  private static final String ENTITY_NOT_FOUND_ERROR =
      "Expected to remove an entity with key '%s' and type '%s' from tenant with key '%s', but the entity doesn't exist.";

  private final UserState userState;

  public TenantRemoveEntityProcessor(
      final TenantState tenantState,
      final UserState userState,
      final AuthorizationCheckBehavior authCheckBehavior,
      final KeyGenerator keyGenerator,
      final Writers writers,
      final CommandDistributionBehavior commandDistributionBehavior) {
    super(tenantState, authCheckBehavior, keyGenerator, writers, commandDistributionBehavior);
    this.userState = userState;
  }

  @Override
  public void processNewCommand(final TypedRecord<TenantRecord> command) {
    final var record = command.getValue();
    final var tenantKey = record.getTenantKey();

    if (!tenantExistsWithKey(tenantKey)) {
      rejectCommand(command, RejectionType.NOT_FOUND, TENANT_NOT_FOUND_ERROR.formatted(tenantKey));
      return;
    }

    if (!isAuthorized(
        command, AuthorizationResourceType.TENANT, PermissionType.UPDATE, record.getTenantId())) {
      return;
    }

    if (!isEntityPresent(record.getEntityKey(), record.getEntityType())) {
      rejectCommand(
          command,
          RejectionType.NOT_FOUND,
          ENTITY_NOT_FOUND_ERROR.formatted(
              record.getEntityKey(), record.getEntityType(), tenantKey));
      return;
    }

    appendEventAndWriteResponse(tenantKey, TenantIntent.ENTITY_REMOVED, record, command);
    distributeCommand(command, keyGenerator.nextKey());
  }

  @Override
  public void processDistributedCommand(final TypedRecord<TenantRecord> command) {
    stateWriter.appendFollowUpEvent(
        command.getValue().getTenantKey(), TenantIntent.ENTITY_REMOVED, command.getValue());
    commandDistributionBehavior.acknowledgeCommand(command);
  }

  private boolean isEntityPresent(final long entityKey, final EntityType entityType) {
    return switch (entityType) {
      case USER -> userState.getUser(entityKey).isPresent();
      case MAPPING ->
          throw new UnsupportedOperationException("MAPPING entity type is not implemented yet.");
      default ->
          throw new IllegalStateException(
              "Unknown or unsupported entity type: '"
                  + entityType
                  + "'. Please contact support for clarification.");
    };
  }
}
