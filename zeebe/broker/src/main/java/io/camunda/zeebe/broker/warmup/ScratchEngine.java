/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.warmup;

import io.camunda.cluster.PartitionId;
import io.camunda.search.clients.SearchClientsProxy;
import io.camunda.secretstore.SecretStoreRegistry;
import io.camunda.zeebe.broker.system.PhysicalTenantContext;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.QueryApiCfg;
import io.camunda.zeebe.broker.transport.commandapi.CommandApiServiceImpl;
import io.camunda.zeebe.db.AccessMetricsConfiguration;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbResources;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.camunda.zeebe.engine.Engine;
import io.camunda.zeebe.engine.processing.EngineProcessors;
import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.processing.streamprocessor.JobStreamer;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessorFactory;
import io.camunda.zeebe.engine.state.query.StateQueryService;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.stream.api.InterPartitionCommandSender;
import io.camunda.zeebe.stream.api.StreamClock;
import io.camunda.zeebe.stream.impl.StreamProcessor;
import io.camunda.zeebe.stream.impl.StreamProcessorMode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.time.Duration;
import java.time.InstantSource;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A single-partition engine assembled from the same production classes as a leader partition —
 * command API request handler, log stream, stream processor, engine processors and RocksDB state —
 * but isolated from the broker: it has its own actor scheduler, meter registry, RocksDB memory and
 * directory, it has no exporters, and it never talks to the network or to other partitions.
 */
@NullMarked
final class ScratchEngine implements AutoCloseable {

  static final int PARTITION_ID = 1;
  private static final long ROCKSDB_MEMORY = 64L * 1024 * 1024;
  private static final Duration STEP_TIMEOUT = Duration.ofSeconds(30);

  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final Deque<ThrowingRunnable> closers = new ArrayDeque<>();

  ScratchEngine(
      final Path directory,
      final BrokerCfg brokerCfg,
      final PhysicalTenantContext tenantContext,
      final ScratchServerTransport transport,
      final JobStreamer jobStreamer)
      throws Exception {
    closers.push(meterRegistry::close);
    try {
      start(directory, brokerCfg, tenantContext, transport, jobStreamer);
    } catch (final Exception e) {
      close();
      throw e;
    }
  }

  private void start(
      final Path directory,
      final BrokerCfg brokerCfg,
      final PhysicalTenantContext tenantContext,
      final ScratchServerTransport transport,
      final JobStreamer jobStreamer)
      throws Exception {
    final var partitionId = new PartitionId("leader-warmup", PARTITION_ID);

    final var scheduler =
        ActorScheduler.newActorScheduler()
            .setSchedulerName("leader-warmup")
            .setCpuBoundActorThreadCount(1)
            .setIoBoundActorThreadCount(1)
            .build();
    scheduler.start();
    closers.push(() -> scheduler.stop().get(STEP_TIMEOUT.toSeconds(), TimeUnit.SECONDS));

    final var rocksDbResources = new RocksDbResources.Shared(ROCKSDB_MEMORY, 1);
    closers.push(rocksDbResources.getSharedCache()::close);
    closers.push(rocksDbResources.getSharedWriteBufferManager()::close);
    final var databaseCfg = brokerCfg.getExperimental().getRocksdb();
    final ZeebeDb<ZbColumnFamilies> zeebeDb =
        new ZeebeRocksDbFactory<ZbColumnFamilies>(
                databaseCfg.createRocksDbConfiguration(),
                brokerCfg.getExperimental().getConsistencyChecks().getSettings(),
                new AccessMetricsConfiguration(databaseCfg.getAccessMetrics()),
                () -> meterRegistry,
                rocksDbResources)
            .createDb(directory.resolve("runtime").toFile());
    closers.push(zeebeDb::close);

    final var flowControlCfg = brokerCfg.getFlowControl();
    final var requestLimitCfg =
        flowControlCfg.getRequest() != null
            ? flowControlCfg.getRequest()
            : brokerCfg.getBackpressure();
    final var clock = StreamClock.controllable(InstantSource.system());
    final var logStream =
        LogStream.builder()
            .withLogStorage(new ScratchLogStorage())
            .withLogName("logStream-leader-warmup")
            .withPartitionId(PARTITION_ID)
            .withMaxFragmentSize((int) brokerCfg.getNetwork().getMaxMessageSizeInBytes())
            .withClock(clock)
            .withRequestLimit(requestLimitCfg.buildLimit())
            .withInFlightCapacity(requestLimitCfg.maxConcurrency() * 20)
            .withMeterRegistry(meterRegistry)
            .build();
    closers.push(logStream::close);

    final var commandApi =
        new CommandApiServiceImpl(partitionId, transport, scheduler, new QueryApiCfg());
    await(scheduler.submitActor(commandApi));
    closers.push(() -> await(commandApi.closeAsync()));

    final InterPartitionCommandSender noInterPartitionCommands =
        (receiverPartitionId, valueType, intent, recordKey, command, authInfo) -> {};
    final var engine =
        new Engine(
            engineProcessorsFactory(tenantContext, jobStreamer),
            brokerCfg.getExperimental().getEngine().createEngineConfiguration(),
            tenantContext.securityConfig());
    final var streamProcessor =
        StreamProcessor.builder()
            .meterRegistry(meterRegistry)
            .logStream(logStream)
            .partitionId(partitionId)
            .actorSchedulingService(scheduler)
            .zeebeDb(zeebeDb)
            .recordProcessors(List.of(engine))
            .commandResponseWriter(commandApi.newCommandResponseWriter())
            .maxCommandsInBatch(brokerCfg.getProcessing().getMaxCommandsInBatch())
            .maxRecoverableRetries(brokerCfg.getProcessing().getMaxRecoverableRetries())
            .maxPendingSideEffects(brokerCfg.getProcessing().getMaxPendingSideEffects())
            .setScheduledTaskCheckInterval(
                brokerCfg.getProcessing().getScheduledTaskCheckInterval())
            .listener(
                processedPosition -> logStream.getFlowControl().onProcessed(processedPosition))
            .streamProcessorMode(StreamProcessorMode.PROCESSING)
            .partitionCommandSender(noInterPartitionCommands)
            .clock(clock)
            .build();
    closers.push(() -> await(streamProcessor.closeAsync()));
    await(streamProcessor.openAsync(false));
    await(
        commandApi.registerHandlers(
            PARTITION_ID, logStream, new StateQueryService(zeebeDb, InstantSource.system())));
  }

  /** Releases everything that was started, in reverse order. */
  @Override
  public void close() {
    while (!closers.isEmpty()) {
      final var closer = closers.pop();
      try {
        closer.run();
      } catch (final Exception e) {
        LeaderWarmup.LOG.debug("Failed to close part of the leader warm-up scratch engine", e);
      }
    }
  }

  private static TypedRecordProcessorFactory engineProcessorsFactory(
      final PhysicalTenantContext tenantContext, final JobStreamer jobStreamer) {
    return recordProcessorContext -> {
      final var partitionCommandSender = recordProcessorContext.getPartitionCommandSender();
      return EngineProcessors.createEngineProcessors(
          recordProcessorContext,
          1,
          new SubscriptionCommandSender(
              recordProcessorContext.getPartitionId(), partitionCommandSender),
          partitionCommandSender,
          tenantContext.featureFlags(),
          jobStreamer,
          SearchClientsProxy.noop(),
          tenantContext.authorizationConverter(),
          new SecretStoreRegistry(Map.of()));
    };
  }

  private static <T> @Nullable T await(final ActorFuture<T> future) throws Exception {
    return future.get(STEP_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }
}
