/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity;

import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessors;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.processing.user.IdentitySetupInitializer;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IdentitySetupIntent;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.FeatureFlags;

public final class IdentitySetupProcessors {
  public static void addIdentitySetupProcessors(
      final KeyGenerator keyGenerator,
      final TypedRecordProcessors typedRecordProcessors,
      final MutableProcessingState processingState,
      final Writers writers,
      final CommandDistributionBehavior distributionBehavior,
      final SecurityConfiguration securityConfig,
      final FeatureFlags featureFlags) {
    typedRecordProcessors
        .onCommand(
            ValueType.IDENTITY_SETUP,
            IdentitySetupIntent.INITIALIZE,
            new IdentitySetupInitializeProcessor(
                processingState, writers, keyGenerator, distributionBehavior))
        .withListener(new IdentitySetupInitializer(securityConfig, featureFlags));
  }
}
