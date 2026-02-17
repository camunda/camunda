/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.globallistener;

import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.identity.authorization.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessors;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.GlobalListenerBatchIntent;
import io.camunda.zeebe.protocol.record.intent.GlobalListenerIntent;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

public class GlobalListenersProcessors {
  public static void addGlobalListenersProcessors(
      final KeyGenerator keyGenerator,
      final TypedRecordProcessors typedRecordProcessors,
      final Writers writers,
      final CommandDistributionBehavior distributionBehavior,
      final EngineConfiguration engineConfiguration,
      final ProcessingState processingState,
      final AuthorizationCheckBehavior authCheckBehavior) {
    typedRecordProcessors
        .onCommand(
            ValueType.GLOBAL_LISTENER_BATCH,
            GlobalListenerBatchIntent.CONFIGURE,
            new GlobalListenerBatchConfigureProcessor(
                keyGenerator, writers, distributionBehavior, processingState))
        .onCommand(
            ValueType.GLOBAL_LISTENER,
            GlobalListenerIntent.CREATE,
            new GlobalListenerCreateProcessor(
                keyGenerator, writers, distributionBehavior, authCheckBehavior, processingState))
        .onCommand(
            ValueType.GLOBAL_LISTENER,
            GlobalListenerIntent.UPDATE,
            new GlobalListenerUpdateProcessor(
                keyGenerator, writers, distributionBehavior, authCheckBehavior, processingState))
        .onCommand(
            ValueType.GLOBAL_LISTENER,
            GlobalListenerIntent.DELETE,
            new GlobalListenerDeleteProcessor(
                keyGenerator, writers, distributionBehavior, authCheckBehavior, processingState))
        .withListener(new GlobalListenersInitializer(engineConfiguration, processingState));
  }
}
