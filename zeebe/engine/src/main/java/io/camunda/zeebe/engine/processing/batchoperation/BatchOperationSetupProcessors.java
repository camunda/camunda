/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation;

import io.camunda.service.search.core.SearchQueryService;
import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessors;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.time.Duration;
import java.util.Set;

public final class BatchOperationSetupProcessors {

  public static void addBatchOperationProcessors(
      final KeyGenerator keyGenerator,
      final TypedRecordProcessors typedRecordProcessors,
      final ProcessingState processingState,
      final Writers writers,
      final CommandDistributionBehavior commandDistributionBehavior,
      final Set<SearchQueryService> searchQueryServices,
      final int partitionId) {
    typedRecordProcessors
        .onCommand(
            ValueType.BATCH_OPERATION,
            BatchOperationIntent.CREATE,
            new BatchOperationActivateProcessor(writers, keyGenerator, commandDistributionBehavior))
        .onCommand(
            ValueType.BATCH_OPERATION_SUBBATCH,
            BatchOperationIntent.CREATE_SUBBATCH,
            new BatchOperationCreateSubbatchProcessor(writers))
        .onCommand(
            ValueType.BATCH_OPERATION_EXECUTION,
            BatchOperationIntent.CANCEL,
            new BatchOperationCancelProcessor(
                writers,
                keyGenerator,
                commandDistributionBehavior,
                processingState))
        .onCommand(
            ValueType.BATCH_OPERATION_EXECUTION,
            BatchOperationIntent.PAUSE,
            new BatchOperationPauseProcessor(
                writers,
                keyGenerator,
                commandDistributionBehavior,
                processingState))
        .onCommand(
            ValueType.BATCH_OPERATION_EXECUTION,
            BatchOperationIntent.RESUME,
            new BatchOperationResumeProcessor(
                writers,
                keyGenerator,
                commandDistributionBehavior,
                processingState))
        .onCommand(
            ValueType.BATCH_OPERATION_EXECUTION,
            BatchOperationIntent.EXECUTE,
            new BatchOperationExecuteProcessor(
                writers, processingState, partitionId))
        // FIXME pass process instance services (or a list of query services) to the scheduler
        // FIXME pass the polling interval from configurations to the scheduler
        .withListener(
            new BatchOperationExecutionScheduler(
                processingState, searchQueryServices, keyGenerator, Duration.ofMillis(100)));
  }
}
