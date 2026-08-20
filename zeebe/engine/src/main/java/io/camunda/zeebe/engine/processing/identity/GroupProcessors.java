/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity;

import io.camunda.security.configuration.EngineSecurityConfig;
import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.identity.adapter.MembershipStateAdapter;
import io.camunda.zeebe.engine.processing.identity.authorization.CslAuthorizationCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessors;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class GroupProcessors {
  public static void addGroupProcessors(
      final TypedRecordProcessors typedRecordProcessors,
      final ProcessingState processingState,
      final CslAuthorizationCheck cslCheck,
      final KeyGenerator keyGenerator,
      final Writers writers,
      final CommandDistributionBehavior commandDistributionBehavior,
      final EngineSecurityConfig securityConfig,
      final MembershipStateAdapter membershipStateAdapter) {
    final var permissionsBehavior = new PermissionsBehavior(processingState, cslCheck);
    typedRecordProcessors.onCommand(
        ValueType.GROUP,
        GroupIntent.CREATE,
        new GroupCreateProcessor(
            processingState.getGroupState(),
            permissionsBehavior,
            keyGenerator,
            writers,
            commandDistributionBehavior));
    typedRecordProcessors.onCommand(
        ValueType.GROUP,
        GroupIntent.UPDATE,
        new GroupUpdateProcessor(
            processingState.getGroupState(),
            keyGenerator,
            permissionsBehavior,
            writers,
            commandDistributionBehavior));
    typedRecordProcessors.onCommand(
        ValueType.GROUP,
        GroupIntent.ADD_ENTITY,
        new GroupAddEntityProcessor(
            processingState,
            permissionsBehavior,
            keyGenerator,
            writers,
            commandDistributionBehavior,
            securityConfig,
            membershipStateAdapter));
    typedRecordProcessors.onCommand(
        ValueType.GROUP,
        GroupIntent.REMOVE_ENTITY,
        new GroupRemoveEntityProcessor(
            processingState,
            permissionsBehavior,
            keyGenerator,
            writers,
            commandDistributionBehavior,
            membershipStateAdapter));
    typedRecordProcessors.onCommand(
        ValueType.GROUP,
        GroupIntent.DELETE,
        new GroupDeleteProcessor(
            processingState,
            permissionsBehavior,
            keyGenerator,
            writers,
            commandDistributionBehavior));
  }
}
