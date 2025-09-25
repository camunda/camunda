/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.common.processing.identity;

import io.camunda.zeebe.engine.common.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.common.processing.streamprocessor.TypedRecordProcessors;
import io.camunda.zeebe.engine.common.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.common.state.mutable.MutableProcessingState;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

public final class AuthorizationProcessors {

  public static void addAuthorizationProcessors(
      final KeyGenerator keyGenerator,
      final TypedRecordProcessors typedRecordProcessors,
      final MutableProcessingState processingState,
      final Writers writers,
      final CommandDistributionBehavior distributionBehavior,
      final AuthorizationCheckBehavior authCheckBehavior) {
    typedRecordProcessors.onCommand(
        ValueType.AUTHORIZATION,
        AuthorizationIntent.CREATE,
        new AuthorizationCreateProcessor(
            writers, keyGenerator, processingState, distributionBehavior, authCheckBehavior));
    typedRecordProcessors.onCommand(
        ValueType.AUTHORIZATION,
        AuthorizationIntent.DELETE,
        new AuthorizationDeleteProcessor(
            writers, keyGenerator, processingState, distributionBehavior, authCheckBehavior));
    typedRecordProcessors.onCommand(
        ValueType.AUTHORIZATION,
        AuthorizationIntent.UPDATE,
        new AuthorizationUpdateProcessor(
            writers, keyGenerator, processingState, distributionBehavior, authCheckBehavior));
  }
}
