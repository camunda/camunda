/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation;

import io.camunda.search.clients.SearchClientsProxy;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.metrics.BatchOperationMetrics;
import io.camunda.zeebe.engine.processing.batchoperation.handlers.CancelProcessInstanceBatchOperationExecutor;
import io.camunda.zeebe.engine.processing.batchoperation.handlers.MigrateProcessInstanceBatchOperationExecutor;
import io.camunda.zeebe.engine.processing.batchoperation.handlers.ModifyProcessInstanceBatchOperationExecutor;
import io.camunda.zeebe.engine.processing.batchoperation.handlers.ResolveIncidentBatchOperationExecutor;
import io.camunda.zeebe.engine.processing.batchoperation.itemprovider.ItemProviderFactory;
import io.camunda.zeebe.engine.processing.batchoperation.scheduler.BatchOperationExecutionScheduler;
import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessors;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.ScheduledTaskState;
import io.camunda.zeebe.engine.state.routing.RoutingInfo;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationChunkIntent;
import io.camunda.zeebe.protocol.record.intent.BatchOperationExecutionIntent;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.util.Map;
import java.util.function.Supplier;

public final class BatchOperationSetupProcessors {

  public static void addBatchOperationProcessors(
      final KeyGenerator keyGenerator,
      final TypedRecordProcessors typedRecordProcessors,
      final Writers writers,
      final CommandDistributionBehavior commandDistributionBehavior,
      final AuthorizationCheckBehavior authorizationCheckBehavior,
      final Supplier<ScheduledTaskState> scheduledTaskStateFactory,
      final SearchClientsProxy searchClientsProxy,
      final ProcessingState processingState,
      final EngineConfiguration engineConfiguration,
      final int partitionId,
      final RoutingInfo routingInfo,
      final BatchOperationMetrics batchOperationMetrics) {
    final var batchExecutionHandlers =
        Map.of(
            BatchOperationType.CANCEL_PROCESS_INSTANCE,
            new CancelProcessInstanceBatchOperationExecutor(writers.command()),
            BatchOperationType.RESOLVE_INCIDENT,
            new ResolveIncidentBatchOperationExecutor(
                writers.command(), processingState.getIncidentState()),
            BatchOperationType.MIGRATE_PROCESS_INSTANCE,
            new MigrateProcessInstanceBatchOperationExecutor(
                writers.command(), processingState.getBatchOperationState()),
            BatchOperationType.MODIFY_PROCESS_INSTANCE,
            new ModifyProcessInstanceBatchOperationExecutor(
                writers.command(), processingState.getElementInstanceState()));

    typedRecordProcessors
        .onCommand(
            ValueType.BATCH_OPERATION_CREATION,
            BatchOperationIntent.CREATE,
            new BatchOperationCreateProcessor(
                writers,
                keyGenerator,
                commandDistributionBehavior,
                authorizationCheckBehavior,
                routingInfo,
                batchOperationMetrics))
        .onCommand(
            ValueType.BATCH_OPERATION_INITIALIZATION,
            BatchOperationIntent.INITIALIZE,
            new BatchOperationInitializeProcessor(writers))
        .onCommand(
            ValueType.BATCH_OPERATION_INITIALIZATION,
            BatchOperationIntent.FINISH_INITIALIZATION,
            new BatchOperationFinishInitializationProcessor(writers, batchOperationMetrics))
        .onCommand(
            ValueType.BATCH_OPERATION_PARTITION_LIFECYCLE,
            BatchOperationIntent.FAIL,
            new BatchOperationFailProcessor(
                writers,
                commandDistributionBehavior,
                keyGenerator,
                batchOperationMetrics,
                processingState))
        .onCommand(
            ValueType.BATCH_OPERATION_CHUNK,
            BatchOperationChunkIntent.CREATE,
            new BatchOperationCreateChunkProcessor(writers))
        .onCommand(
            ValueType.BATCH_OPERATION_EXECUTION,
            BatchOperationExecutionIntent.EXECUTE,
            new BatchOperationExecuteProcessor(
                writers,
                processingState,
                commandDistributionBehavior,
                keyGenerator,
                batchExecutionHandlers,
                batchOperationMetrics))
        .onCommand(
            ValueType.BATCH_OPERATION_LIFECYCLE_MANAGEMENT,
            BatchOperationIntent.CANCEL,
            new BatchOperationCancelProcessor(
                writers,
                commandDistributionBehavior,
                processingState,
                authorizationCheckBehavior,
                keyGenerator,
                batchOperationMetrics))
        .onCommand(
            ValueType.BATCH_OPERATION_LIFECYCLE_MANAGEMENT,
            BatchOperationIntent.SUSPEND,
            new BatchOperationSuspendProcessor(
                writers,
                commandDistributionBehavior,
                processingState,
                authorizationCheckBehavior,
                keyGenerator,
                batchOperationMetrics))
        .onCommand(
            ValueType.BATCH_OPERATION_LIFECYCLE_MANAGEMENT,
            BatchOperationIntent.RESUME,
            new BatchOperationResumeProcessor(
                writers,
                commandDistributionBehavior,
                processingState,
                authorizationCheckBehavior,
                keyGenerator,
                batchOperationMetrics))
        .onCommand(
            ValueType.BATCH_OPERATION_PARTITION_LIFECYCLE,
            BatchOperationIntent.COMPLETE_PARTITION,
            new BatchOperationLeadPartitionCompleteProcessor(
                writers, processingState, commandDistributionBehavior, batchOperationMetrics))
        .onCommand(
            ValueType.BATCH_OPERATION_PARTITION_LIFECYCLE,
            BatchOperationIntent.FAIL_PARTITION,
            new BatchOperationLeadPartitionFailProcessor(
                writers, processingState, commandDistributionBehavior, batchOperationMetrics))
        .withListener(
            new BatchOperationExecutionScheduler(
                scheduledTaskStateFactory,
                new ItemProviderFactory(searchClientsProxy, batchOperationMetrics, partitionId),
                engineConfiguration,
                partitionId,
                batchOperationMetrics));
  }
}
