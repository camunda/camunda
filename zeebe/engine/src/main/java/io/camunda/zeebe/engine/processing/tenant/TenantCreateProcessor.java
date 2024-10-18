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

public class TenantCreateProcessor extends AbstractTenantProcessor {

  private static final String TENANT_ALREADY_EXISTS_ERROR =
      "Expected to create tenant with ID '%s', but a tenant with this ID already exists";

  public TenantCreateProcessor(
      final TenantState tenantState,
      final AuthorizationCheckBehavior authCheckBehavior,
      final KeyGenerator keyGenerator,
      final Writers writers,
      final CommandDistributionBehavior commandDistributionBehavior) {
    super(tenantState, authCheckBehavior, keyGenerator, writers, commandDistributionBehavior);
  }

  @Override
  public void processNewCommand(final TypedRecord<TenantRecord> command) {

    if (!isAuthorized(command, AuthorizationResourceType.TENANT, PermissionType.CREATE)) {
      return;
    }

    final var record = command.getValue();
    if (tenantExistsWithId(record.getTenantId())) {
      rejectCommand(
          command,
          RejectionType.ALREADY_EXISTS,
          TENANT_ALREADY_EXISTS_ERROR.formatted(record.getTenantId()));
    } else {
      final long key = keyGenerator.nextKey();
      record.setTenantKey(key);
      appendEventAndWriteResponse(key, TenantIntent.CREATED, record, command);
      distributeCommand(command, record.getTenantKey());
    }
  }

  @Override
  public void processDistributedCommand(final TypedRecord<TenantRecord> command) {
    stateWriter.appendFollowUpEvent(command.getKey(), TenantIntent.CREATED, command.getValue());
    commandDistributionBehavior.acknowledgeCommand(command);
  }
}
