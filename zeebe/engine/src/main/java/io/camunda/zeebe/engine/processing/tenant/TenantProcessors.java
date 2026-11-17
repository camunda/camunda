/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.tenant;

import io.camunda.security.configuration.EngineSecurityConfig;
import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.identity.PermissionsBehavior;
import io.camunda.zeebe.engine.processing.identity.adapter.AuthorizationScopeStateAdapter;
import io.camunda.zeebe.engine.processing.identity.adapter.MembershipStateAdapter;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessors;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

public class TenantProcessors {

  public static void addTenantProcessors(
      final TypedRecordProcessors typedRecordProcessors,
      final ProcessingState processingState,
      final PermissionsBehavior permissionsBehavior,
      final KeyGenerator keyGenerator,
      final Writers writers,
      final CommandDistributionBehavior commandDistributionBehavior,
      final EngineSecurityConfig securityConfig,
      final AuthorizationScopeStateAdapter authorizationScopeStateAdapter,
      final MembershipStateAdapter membershipStateAdapter) {
    typedRecordProcessors
        .onCommand(
            ValueType.TENANT,
            TenantIntent.CREATE,
            new TenantCreateProcessor(
                processingState.getTenantState(),
                permissionsBehavior,
                keyGenerator,
                writers,
                commandDistributionBehavior))
        .onCommand(
            ValueType.TENANT,
            TenantIntent.UPDATE,
            new TenantUpdateProcessor(
                processingState.getTenantState(),
                permissionsBehavior,
                keyGenerator,
                writers,
                commandDistributionBehavior))
        .onCommand(
            ValueType.TENANT,
            TenantIntent.ADD_ENTITY,
            new TenantAddEntityProcessor(
                processingState,
                permissionsBehavior,
                keyGenerator,
                writers,
                commandDistributionBehavior,
                securityConfig,
                membershipStateAdapter))
        .onCommand(
            ValueType.TENANT,
            TenantIntent.REMOVE_ENTITY,
            new TenantRemoveEntityProcessor(
                processingState,
                permissionsBehavior,
                keyGenerator,
                writers,
                commandDistributionBehavior,
                membershipStateAdapter))
        .onCommand(
            ValueType.TENANT,
            TenantIntent.DELETE,
            new TenantDeleteProcessor(
                processingState,
                permissionsBehavior,
                keyGenerator,
                writers,
                commandDistributionBehavior,
                authorizationScopeStateAdapter,
                membershipStateAdapter));
  }
}
