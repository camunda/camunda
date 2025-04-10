/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation;

import io.camunda.search.clients.SearchClientsProxy;
import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessors;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.ScheduledTaskState;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationChunkIntent;
import io.camunda.zeebe.protocol.record.intent.BatchOperationExecutionIntent;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.time.Duration;
import java.util.function.Supplier;

public final class BatchOperationSetupProcessors {

  public static void addBatchOperationProcessors(
      final KeyGenerator keyGenerator,
      final TypedRecordProcessors typedRecordProcessors,
      final Writers writers,
      final CommandDistributionBehavior commandDistributionBehavior,
      final Supplier<ScheduledTaskState> scheduledTaskStateFactory,
      final SearchClientsProxy searchClientsProxy,
      final ProcessingState processingState,
      final int partitionId) {
    typedRecordProcessors
        .onCommand(
            ValueType.BATCH_OPERATION_CREATION,
            BatchOperationIntent.CREATE,
            new BatchOperationCreateProcessor(writers, keyGenerator, commandDistributionBehavior))
        .onCommand(
            ValueType.BATCH_OPERATION_CREATION,
            BatchOperationIntent.START,
            new BatchOperationStartProcessor(writers))
        .onCommand(
            ValueType.BATCH_OPERATION_CREATION,
            BatchOperationIntent.FAIL,
            new BatchOperationFailProcessor(writers))
        .onCommand(
            ValueType.BATCH_OPERATION_CHUNK,
            BatchOperationChunkIntent.CREATE,
            new BatchOperationCreateChunkProcessor(writers))
        .onCommand(
            ValueType.BATCH_OPERATION_EXECUTION,
            BatchOperationExecutionIntent.EXECUTE,
            new BatchOperationExecuteProcessor(writers, processingState, partitionId))
        .onCommand(
            ValueType.BATCH_OPERATION_LIFECYCLE_MANAGEMENT,
            BatchOperationIntent.CANCEL,
            new BatchOperationCancelProcessor(
                writers, commandDistributionBehavior, processingState, keyGenerator))
        .onCommand(
            ValueType.BATCH_OPERATION_LIFECYCLE_MANAGEMENT,
            BatchOperationIntent.PAUSE,
            new BatchOperationPauseProcessor(
                writers, commandDistributionBehavior, processingState, keyGenerator))
        .onCommand(
            ValueType.BATCH_OPERATION_LIFECYCLE_MANAGEMENT,
            BatchOperationIntent.RESUME,
            new BatchOperationResumeProcessor(
                writers, commandDistributionBehavior, processingState, keyGenerator))
        .withListener(
            new BatchOperationExecutionScheduler(
                scheduledTaskStateFactory, searchClientsProxy, Duration.ofMillis(1000)));
  }
}
