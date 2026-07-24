/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.secretreference;

import io.camunda.secretstore.SecretCache;
import io.camunda.secretstore.SecretStore;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessors;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.ScheduledTaskState;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.SecretReferenceIntent;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.util.Map;
import java.util.function.Supplier;

public final class SecretReferenceProcessors {

  private SecretReferenceProcessors() {}

  public static void addSecretReferenceProcessors(
      final TypedRecordProcessors typedRecordProcessors,
      final Writers writers,
      final KeyGenerator keyGenerator,
      final ProcessingState processingState,
      final Supplier<ScheduledTaskState> scheduledTaskStateFactory,
      final Map<String, SecretStore> secretStores,
      final Map<String, SecretCache> secretCaches,
      final EngineConfiguration config) {
    typedRecordProcessors.onCommand(
        ValueType.SECRET_REFERENCE,
        SecretReferenceIntent.RESOLUTION_COMPLETE,
        new SecretReferenceResolutionCompleteProcessor());
    typedRecordProcessors.onCommand(
        ValueType.SECRET_REFERENCE,
        SecretReferenceIntent.RESOLUTION_FAIL,
        new SecretReferenceResolutionFailProcessor());
    typedRecordProcessors.onCommand(
        ValueType.SECRET_REFERENCE,
        SecretReferenceIntent.BATCH_REACTIVATE_JOBS,
        new SecretReferenceBatchReactivateJobsProcessor(
            writers, keyGenerator, processingState.getSecretReferenceState()));
    typedRecordProcessors.onCommand(
        ValueType.SECRET_REFERENCE,
        SecretReferenceIntent.BATCH_CREATE_INCIDENTS,
        new SecretReferenceBatchCreateIncidentsProcessor());

    final var scheduler =
        new SecretResolutionScheduler(
            scheduledTaskStateFactory, secretStores, secretCaches, config);
    typedRecordProcessors.withListener(scheduler);
  }
}
