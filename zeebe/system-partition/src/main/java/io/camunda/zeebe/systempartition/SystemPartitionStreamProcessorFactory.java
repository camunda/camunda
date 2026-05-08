/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.systempartition;

import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeAppliers;
import io.camunda.zeebe.engine.Engine;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessorFactory;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessors;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.stream.api.CommandResponseWriter;
import io.camunda.zeebe.stream.api.InterPartitionCommandSender;
import io.camunda.zeebe.stream.api.RecordProcessor;
import io.camunda.zeebe.stream.impl.StreamProcessor;
import io.camunda.zeebe.stream.impl.StreamProcessorMode;
import io.camunda.zeebe.systempartition.processors.BackupControlPlaneProcessors;
import io.camunda.zeebe.systempartition.processors.ClusterConfigurationProcessors;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;

/**
 * Factory that constructs the system-partition's {@link StreamProcessor}.
 *
 * <p>The system partition runs the same workflow {@link Engine} as data partitions; the engine's
 * {@link TypedRecordProcessorFactory} is augmented to register the cluster-configuration and
 * backup-metadata processors alongside the standard engine processors. The resulting processor
 * pipeline operates on the system-partition's own {@link LogStream} and {@link ZeebeDb}.
 *
 * <p>The broker bootstrap (Phase 3) provides the engine factory and infrastructure dependencies.
 * This factory only knows how to compose the system-specific processors with whatever engine the
 * caller supplied.
 */
public final class SystemPartitionStreamProcessorFactory {

  private SystemPartitionStreamProcessorFactory() {}

  /**
   * Build the system-partition stream processor.
   *
   * @param logStream the system-partition log stream (see {@link SystemPartitionLogStream})
   * @param db the system-partition RocksDB instance
   * @param scheduler the broker actor scheduler
   * @param nodeId the local node id (used for record metadata)
   * @param mode {@link StreamProcessorMode#PROCESSING} on the leader, {@link
   *     StreamProcessorMode#REPLAY} on followers
   * @param engineProcessorFactory the engine's typed-record-processor factory; the standard
   *     workflow engine processors are obtained from it, then augmented with the cluster-config and
   *     backup processors registered by this factory
   * @param engineConfig the engine configuration
   * @param securityConfig the security configuration
   * @param appliers cluster configuration change appliers (used by the apply-operation processor)
   * @param commandResponseWriter writer for synchronous responses to user commands
   * @param partitionCommandSender used by the engine for inter-partition fan-out
   * @param meterRegistry per-stream metrics
   * @return a fresh, unstarted {@link StreamProcessor}
   */
  public static StreamProcessor build(
      final LogStream logStream,
      final ZeebeDb<ZbColumnFamilies> db,
      final ActorSchedulingService scheduler,
      final int nodeId,
      final StreamProcessorMode mode,
      final TypedRecordProcessorFactory engineProcessorFactory,
      final EngineConfiguration engineConfig,
      final SecurityConfiguration securityConfig,
      final ConfigurationChangeAppliers appliers,
      final CommandResponseWriter commandResponseWriter,
      final InterPartitionCommandSender partitionCommandSender,
      final MeterRegistry meterRegistry) {

    final TypedRecordProcessorFactory composed =
        ctx -> {
          final TypedRecordProcessors processors = engineProcessorFactory.createProcessors(ctx);
          final var ccState = ctx.getProcessingState().getClusterConfigurationState();
          ClusterConfigurationProcessors.register(
              processors,
              ccState,
              ctx.getWriters(),
              ctx.getProcessingState().getKeyGenerator(),
              appliers);

          BackupControlPlaneProcessors.register(
              processors,
              ctx.getProcessingState().getBackupMetadataState(),
              ctx.getWriters(),
              ctx.getProcessingState().getKeyGenerator());
          return processors;
        };

    final Engine engine = new Engine(composed, engineConfig, securityConfig);
    final List<RecordProcessor> recordProcessors = List.of(engine);

    return StreamProcessor.builder()
        .logStream(logStream)
        .zeebeDb(db)
        .actorSchedulingService(scheduler)
        .nodeId(nodeId)
        .recordProcessors(recordProcessors)
        .commandResponseWriter(commandResponseWriter)
        .partitionCommandSender(partitionCommandSender)
        .streamProcessorMode(mode)
        .meterRegistry(meterRegistry)
        .build();
  }
}
