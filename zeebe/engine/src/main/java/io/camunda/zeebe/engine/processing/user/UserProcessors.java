/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.user;

import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessors;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

public class UserProcessors {
  public static void addUserProcessors(
      final KeyGenerator keyGenerator,
      final TypedRecordProcessors typedRecordProcessors,
      final MutableProcessingState processingState,
      final Writers writers,
      final CommandDistributionBehavior distributionBehavior,
      final AuthorizationCheckBehavior authCheckBehavior) {
    typedRecordProcessors
        .onCommand(
            ValueType.USER,
            UserIntent.CREATE,
            new UserCreateProcessor(
                keyGenerator, processingState, writers, distributionBehavior, authCheckBehavior))
        .onCommand(
            ValueType.USER,
            UserIntent.UPDATE,
            new UserUpdateProcessor(
                keyGenerator, processingState, writers, distributionBehavior, authCheckBehavior))
        .onCommand(
            ValueType.USER,
            UserIntent.DELETE,
            new UserDeleteProcessor(
                keyGenerator, processingState, writers, distributionBehavior, authCheckBehavior))
        .onCommand(
            ValueType.USER,
            UserIntent.CREATE_INITIAL_ADMIN,
            new UserCreateInitialAdminProcessor(
                keyGenerator, processingState, writers, authCheckBehavior));
  }
}
