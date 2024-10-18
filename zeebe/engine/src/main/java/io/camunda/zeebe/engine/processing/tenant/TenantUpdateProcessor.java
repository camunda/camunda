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
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.util.Optional;

public class TenantUpdateProcessor extends AbstractTenantProcessor {

  private static final String TENANT_ALREADY_EXISTS_ERROR =
      "Expected to update tenant with ID '%s', but a tenant with this ID already exists.";

  private static final String TENANT_NOT_FOUND_ERROR =
      "Expected to update tenant with key '%s', but no tenant with this key exists.";

  public TenantUpdateProcessor(
      final TenantState tenantState,
      final AuthorizationCheckBehavior authCheckBehavior,
      final KeyGenerator keyGenerator,
      final Writers writers,
      final CommandDistributionBehavior commandDistributionBehavior) {
    super(tenantState, authCheckBehavior, keyGenerator, writers, commandDistributionBehavior);
  }

  @Override
  public void processNewCommand(final TypedRecord<TenantRecord> command) {
    if (!isAuthorized(
        command,
        AuthorizationResourceType.TENANT,
        PermissionType.UPDATE,
        command.getValue().getTenantId())) {
      return;
    }

    final var record = command.getValue();
    final var tenantKey = record.getTenantKey();

    tenantState
        .getTenantByKey(tenantKey)
        .ifPresentOrElse(
            existingRecord -> {
              if (tenantIdConflict(record)) {
                rejectCommand(
                    command,
                    RejectionType.ALREADY_EXISTS,
                    TENANT_ALREADY_EXISTS_ERROR.formatted(record.getTenantId()));
              } else {
                updateExistingTenant(existingRecord, record);
                appendEventAndWriteResponse(
                    existingRecord.getTenantKey(), TenantIntent.UPDATED, existingRecord, command);
                distributeCommand(command, keyGenerator.nextKey());
              }
            },
            () ->
                rejectCommand(
                    command, RejectionType.NOT_FOUND, TENANT_NOT_FOUND_ERROR.formatted(tenantKey)));
  }

  @Override
  public void processDistributedCommand(final TypedRecord<TenantRecord> command) {
    stateWriter.appendFollowUpEvent(
        command.getValue().getTenantKey(), TenantIntent.UPDATED, command.getValue());
    commandDistributionBehavior.acknowledgeCommand(command);
  }

  private boolean tenantIdConflict(final TenantRecord record) {
    final Optional<Long> tenantKeyById = tenantState.getTenantKeyById(record.getTenantId());
    return tenantKeyById.isPresent() && !tenantKeyById.get().equals(record.getTenantKey());
  }

  private void updateExistingTenant(
      final TenantRecord existingTenant, final TenantRecord updateRecord) {
    if (!updateRecord.getTenantId().isEmpty()) {
      existingTenant.setTenantId(updateRecord.getTenantId());
    }
    if (!updateRecord.getName().isEmpty()) {
      existingTenant.setName(updateRecord.getName());
    }
  }
}
