/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.bootstrap;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.atomix.primitive.partition.impl.DefaultPartitionManagementService;
import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.partition.RaftPartition;
import io.atomix.raft.partition.impl.RaftPartitionServer;
import io.atomix.raft.storage.log.entry.ApplicationEntry;
import io.atomix.raft.zeebe.ZeebeLogAppender;
import io.camunda.client.CamundaClient;
import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.logstreams.AtomixLogStorage;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.SystemPartitionCfg;
import io.camunda.zeebe.db.AccessMetricsConfiguration;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.rocksdb.ChecksumProviderRocksDBImpl;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.camunda.zeebe.dynamic.config.changes.NoopConfigurationChangeAppliers;
import io.camunda.zeebe.engine.processing.EngineProcessors;
import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.processing.streamprocessor.JobStreamer;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessorFactory;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.encoding.AuthInfo;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.scheduler.SchedulingHints;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.startup.StartupStep;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStore;
import io.camunda.zeebe.stream.api.CommandResponseWriter;
import io.camunda.zeebe.stream.api.InterPartitionCommandSender;
import io.camunda.zeebe.stream.impl.StreamProcessor;
import io.camunda.zeebe.stream.impl.StreamProcessorMode;
import io.camunda.zeebe.systempartition.SystemPartitionBpmnAutoDeployer;
import io.camunda.zeebe.systempartition.SystemPartitionFacadeImpl;
import io.camunda.zeebe.systempartition.SystemPartitionFactory;
import io.camunda.zeebe.systempartition.SystemPartitionLogStream;
import io.camunda.zeebe.systempartition.SystemPartitionMirror;
import io.camunda.zeebe.systempartition.SystemPartitionStreamProcessorFactory;
import io.camunda.zeebe.systempartition.backup.BackupOrchestrator;
import io.camunda.zeebe.util.FileUtil;
import io.camunda.zeebe.util.buffer.BufferWriter;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;

/**
 * Bootstraps the system partition's Raft replica, stream processor, and facade.
 *
 * <p>Skipped when {@code experimental.systemPartition.enabled = false} (the default). When the flag
 * is on:
 *
 * <ol>
 *   <li>Resolve the static membership: lowest-{@code nodeId} brokers, count = {@code
 *       systemPartition.replicationFactor} (or {@code cluster.replicationFactor} if 0).
 *   <li>Create a {@link FileBasedSnapshotStore} for the partition directory under {@code
 *       {dataDir}/system/partitions/1/} and submit it.
 *   <li>Build the {@link RaftPartition} and bootstrap it.
 *   <li>Build {@link AtomixLogStorage} on top of the Raft server, then a {@link LogStream}.
 *   <li>Open a {@link ZeebeDb} for the system-partition state.
 *   <li>Build and submit the system-partition {@link StreamProcessor}.
 *   <li>Build the {@link SystemPartitionFacadeImpl} and the {@link SystemPartitionMirror} actor;
 *       wire the Raft role-change listener to the facade.
 *   <li>Publish the facade onto the {@link BrokerStartupContext}.
 * </ol>
 *
 * <p>Hackday scope: leadership-aware writability is delegated to the Raft layer. Writes from
 * non-leader brokers fail through {@link
 * io.camunda.zeebe.systempartition.SystemPartition.NotLeaderException}. The single shared {@link
 * AtomixLogStorage} resolves the current appender on each call so the same instance can serve both
 * leader and follower roles for the lifetime of the broker.
 */
public final class SystemPartitionStep implements StartupStep<BrokerStartupContext> {

  private static final Logger LOG = Loggers.SYSTEM_LOGGER;

  @Override
  public String getName() {
    return "System Partition";
  }

  @Override
  public ActorFuture<BrokerStartupContext> startup(final BrokerStartupContext context) {
    final BrokerCfg cfg = context.getBrokerConfiguration();
    final SystemPartitionCfg sysCfg = cfg.getExperimental().getSystemPartition();

    if (!sysCfg.isEnabled()) {
      LOG.debug("System partition is disabled; skipping system partition bootstrap.");
      return CompletableActorFuture.completed(context);
    }

    final CompletableActorFuture<BrokerStartupContext> result = new CompletableActorFuture<>();

    try {
      final Set<MemberId> clusterMembers = staticClusterMembers(cfg);
      final int requestedRf = sysCfg.getReplicationFactor();
      final int rf =
          requestedRf == SystemPartitionCfg.REPLICATION_FACTOR_MATCH_DATA
              ? cfg.getCluster().getReplicationFactor()
              : requestedRf;
      final Set<MemberId> systemMembers =
          SystemPartitionFactory.resolveSystemPartitionMembers(clusterMembers, rf);
      final PartitionMetadata metadata =
          SystemPartitionFactory.buildPartitionMetadata(systemMembers);

      final Path dir = SystemPartitionFactory.getPartitionDirectory(cfg.getData().getDirectory());
      FileUtil.ensureDirectoryExists(dir);

      LOG.info(
          "Bootstrapping system partition with members {} (replicationFactor={}); data dir={}",
          systemMembers,
          rf,
          dir);

      // Scope a partition-tagged registry so meters created by the snapshot store + RaftPartition
      // share the [bootstrap, partition] tag-key set Prometheus requires. Non-numeric value to
      // avoid collisions with data-partition ids.
      final MeterRegistry partitionRegistry =
          MicrometerUtil.wrap(
              context.getMeterRegistry(),
              Tags.of(
                  "partition",
                  SystemPartitionFactory.GROUP_NAME
                      + "-"
                      + SystemPartitionFactory.SYSTEM_PARTITION_ID));

      final FileBasedSnapshotStore snapshotStore =
          new FileBasedSnapshotStore(
              cfg.getCluster().getNodeId(),
              SystemPartitionFactory.SYSTEM_PARTITION_ID,
              dir,
              new ChecksumProviderRocksDBImpl(),
              partitionRegistry);

      context
          .getActorSchedulingService()
          .submitActor(snapshotStore, SchedulingHints.ioBound())
          .onComplete(
              (snapshotStarted, snapshotErr) -> {
                if (snapshotErr != null) {
                  result.completeExceptionally(snapshotErr);
                  return;
                }
                bootstrapRaftAndStream(
                    context, metadata, dir, snapshotStore, partitionRegistry, result);
              });
    } catch (final IOException e) {
      result.completeExceptionally(new UncheckedIOException(e));
    }
    return result;
  }

  @Override
  public ActorFuture<BrokerStartupContext> shutdown(final BrokerStartupContext context) {
    // For hackday scope: rely on broker actor scheduler shutdown to clean up. A future revision
    // should explicitly close (in reverse) the mirror, stream processor, log stream, ZeebeDb,
    // RaftPartition, and snapshot store, mirroring SnapshotStoreStep.
    context.setSystemPartition(null);
    return CompletableActorFuture.completed(context);
  }

  private void bootstrapRaftAndStream(
      final BrokerStartupContext context,
      final PartitionMetadata metadata,
      final Path dir,
      final FileBasedSnapshotStore snapshotStore,
      final MeterRegistry partitionRegistry,
      final CompletableActorFuture<BrokerStartupContext> result) {
    try {
      final RaftPartition partition =
          SystemPartitionFactory.createRaftPartition(
              metadata, dir, 32L * 1024 * 1024, 1024L * 1024 * 1024, partitionRegistry);

      final var managementService =
          new DefaultPartitionManagementService(
              context.getClusterServices().getMembershipService(),
              context.getClusterServices().getCommunicationService());

      partition
          .bootstrap(managementService, snapshotStore)
          .whenComplete(
              (raft, raftErr) -> {
                if (raftErr != null) {
                  result.completeExceptionally(raftErr);
                  return;
                }
                try {
                  buildStreamPipeline(context, partition, dir, partitionRegistry, result);
                } catch (final Exception e) {
                  result.completeExceptionally(e);
                }
              });
    } catch (final Exception e) {
      result.completeExceptionally(e);
    }
  }

  private void buildStreamPipeline(
      final BrokerStartupContext context,
      final RaftPartition partition,
      final Path dir,
      final MeterRegistry partitionRegistry,
      final CompletableActorFuture<BrokerStartupContext> result) {

    final BrokerCfg cfg = context.getBrokerConfiguration();
    final RaftPartitionServer server = partition.getServer();

    // 1) AtomixLogStorage backed by the Raft server. The appender resolves dynamically per call so
    // a single LogStorage instance serves both leader (writable) and follower (read-only) roles.
    final AtomixLogStorage logStorage =
        new AtomixLogStorage(server::openReader, new DynamicLogAppender(server));
    server.addCommitListener(logStorage);

    // 2) LogStream over the AtomixLogStorage.
    final int maxFragmentSize = (int) cfg.getNetwork().getMaxMessageSizeInBytes();
    final LogStream logStream =
        SystemPartitionLogStream.build(partition, logStorage, maxFragmentSize, partitionRegistry);

    // 3) ZeebeDb at {dataDir}/system/partitions/1/state — minimal, one-partition factory; not
    // shared with data partitions.
    final var rocksdbCfg = cfg.getExperimental().getRocksdb();
    final var consistencyChecks = cfg.getExperimental().getConsistencyChecks();
    final var zeebeFactory =
        new ZeebeRocksDbFactory<ZbColumnFamilies>(
            rocksdbCfg.createRocksDbConfiguration(),
            consistencyChecks.getSettings(),
            new AccessMetricsConfiguration(
                rocksdbCfg.getAccessMetrics(), SystemPartitionFactory.SYSTEM_PARTITION_ID),
            () -> partitionRegistry);
    final File stateDir = dir.resolve("state").toFile();
    if (!stateDir.exists() && !stateDir.mkdirs()) {
      throw new IllegalStateException(
          "Failed to create system-partition state directory " + stateDir);
    }
    final ZeebeDb<ZbColumnFamilies> db = zeebeFactory.createDb(stateDir);

    // 4) StreamProcessor.
    // Mount the full BPMN engine (Approach A from the design doc): the system partition hosts
    // cluster-management BPMNs (scale-operation, exporter-operation, modification_starter,
    // checkpoint_scheduler, retention_scheduler) which require the standard engine processors
    // (DeploymentProcessor, JobProcessor, ProcessInstanceCreationProcessor, ...). The
    // SystemPartitionStreamProcessorFactory composes the engine processors with the
    // cluster-configuration and backup-metadata processors registered on top.
    //
    // Hackday MVP notes:
    //  - JobStreamer is a no-op: workers on the system partition use standard polling, not push.
    //  - InterPartitionCommandSender is the no-op stub below; backup control-plane fan-out
    //    (Phase 6) will swap this for a BrokerClient-backed sender.
    final var featureFlags = cfg.getExperimental().getFeatures().toFeatureFlags();
    final int partitionsCount = cfg.getCluster().getPartitionsCount();
    final var searchClientsProxy = context.getSearchClientsProxy();
    final var brokerRequestAuthorizationConverter =
        context.getBrokerRequestAuthorizationConverter();
    final TypedRecordProcessorFactory engineFactory =
        ctx -> {
          final var partitionCommandSender = ctx.getPartitionCommandSender();
          final var subscriptionCommandSender =
              new SubscriptionCommandSender(ctx.getPartitionId(), partitionCommandSender);
          return EngineProcessors.createEngineProcessors(
              ctx,
              partitionsCount,
              subscriptionCommandSender,
              partitionCommandSender,
              featureFlags,
              JobStreamer.noop(),
              searchClientsProxy,
              brokerRequestAuthorizationConverter);
        };
    final StreamProcessorMode mode =
        partition.getRole() == Role.LEADER
            ? StreamProcessorMode.PROCESSING
            : StreamProcessorMode.REPLAY;

    final StreamProcessor streamProcessor =
        SystemPartitionStreamProcessorFactory.build(
            logStream,
            db,
            context.getActorSchedulingService(),
            cfg.getCluster().getNodeId(),
            mode,
            engineFactory,
            cfg.getExperimental().getEngine().createEngineConfiguration(),
            context.getSecurityConfiguration(),
            new NoopConfigurationChangeAppliers(),
            new NoopCommandResponseWriter(),
            new NoopInterPartitionCommandSender(),
            partitionRegistry);

    streamProcessor
        .openAsync(false)
        .onComplete(
            (ok, openErr) -> {
              if (openErr != null) {
                result.completeExceptionally(openErr);
                return;
              }
              startMirrorAndPublish(context, partition, logStream, streamProcessor, result);
            });
  }

  private void startMirrorAndPublish(
      final BrokerStartupContext context,
      final RaftPartition partition,
      final LogStream logStream,
      final StreamProcessor streamProcessor,
      final CompletableActorFuture<BrokerStartupContext> result) {

    final SystemPartitionFacadeImpl facade =
        new SystemPartitionFacadeImpl(partition, logStream.newLogStreamWriter());

    final SystemPartitionMirror mirror = new SystemPartitionMirror(logStream, facade);

    context
        .getActorSchedulingService()
        .submitActor(mirror)
        .onComplete(
            (mirrorOk, mirrorErr) -> {
              if (mirrorErr != null) {
                result.completeExceptionally(mirrorErr);
                return;
              }
              partition.addRoleChangeListener(
                  (newRole, term) -> facade.notifyLeaderChange(newRole == Role.LEADER));

              // Wire the BPMN auto-deployer if the embedded gateway is enabled.
              final BrokerCfg cfg = context.getBrokerConfiguration();
              if (cfg.getGateway().isEnable()) {
                final int grpcPort = cfg.getGateway().getNetwork().getPort();
                final String host = cfg.getGateway().getNetwork().getHost();
                final CamundaClient camundaClient =
                    CamundaClient.newClientBuilder()
                        .grpcAddress(java.net.URI.create("http://" + host + ":" + grpcPort))
                        .preferRestOverGrpc(false)
                        .build();
                final SystemPartitionBpmnAutoDeployer deployer =
                    new SystemPartitionBpmnAutoDeployer(camundaClient);
                deployer.register(facade);
              } else {
                LOG.info(
                    "Embedded gateway is disabled — system-partition BPMN auto-deployer not started");
              }

              // Submit the BackupOrchestrator: a leader-only actor that drives backup fan-out
              // to data partitions when a BACKUP_METADATA RECORDED PENDING row is observed. The
              // actor itself ignores events when isLeader() is false, so it's safe to keep it
              // running on every replica that hosts the system partition.
              final var orchestrator = new BackupOrchestrator(facade, context.getBrokerClient());
              context
                  .getActorSchedulingService()
                  .submitActor(orchestrator)
                  .onComplete(
                      (orchestratorOk, orchestratorErr) -> {
                        if (orchestratorErr != null) {
                          LOG.warn(
                              "Failed to submit BackupOrchestrator on system partition; backup fan-out disabled",
                              orchestratorErr);
                        }
                      });

              // Publish the facade to the static accessor used by BackupApiRequestHandler when
              // routing TAKE_BACKUP requests through the system partition.
              io.camunda.zeebe.broker.transport.backupapi.BackupApiRequestHandler
                  .setSystemPartition(facade);

              context.setSystemPartition(facade);
              result.complete(context);
            });
  }

  private static Set<MemberId> staticClusterMembers(final BrokerCfg cfg) {
    final int clusterSize = cfg.getCluster().getClusterSize();
    return IntStream.range(0, clusterSize)
        .mapToObj(id -> MemberId.from(Integer.toString(id)))
        .collect(Collectors.toSet());
  }

  // ------------------------------------------------------------------
  // Adapter / no-op support classes for the system-partition stream.
  // ------------------------------------------------------------------

  /**
   * Resolves the current leader appender from the Raft server on each call. Throws when the server
   * is not currently the leader; callers should detect this via the {@code SystemPartition} facade
   * (which checks {@link RaftPartition#getRole()} before attempting a write).
   */
  private static final class DynamicLogAppender implements ZeebeLogAppender {

    private final RaftPartitionServer server;

    DynamicLogAppender(final RaftPartitionServer server) {
      this.server = server;
    }

    @Override
    public void appendEntry(final ApplicationEntry entry, final AppendListener appendListener) {
      final var maybeAppender = server.getAppender();
      if (maybeAppender.isPresent()) {
        maybeAppender.get().appendEntry(entry, appendListener);
      } else {
        appendListener.onWriteError(
            new IllegalStateException(
                "System partition is not currently the leader; cannot append entry "
                    + "(positions "
                    + entry.lowestPosition()
                    + " - "
                    + entry.highestPosition()
                    + ")"));
      }
    }
  }

  /**
   * The system partition does not produce synchronous user-facing responses (cluster-configuration
   * commands flow back through {@code SystemPartition.submitCommand}'s {@code ActorFuture}, which
   * is correlated via the mirror's commit observation). All response-writer calls are no-ops.
   */
  private static final class NoopCommandResponseWriter implements CommandResponseWriter {
    @Override
    public CommandResponseWriter partitionId(final int partitionId) {
      return this;
    }

    @Override
    public CommandResponseWriter key(final long key) {
      return this;
    }

    @Override
    public CommandResponseWriter intent(final Intent intent) {
      return this;
    }

    @Override
    public CommandResponseWriter recordType(final RecordType type) {
      return this;
    }

    @Override
    public CommandResponseWriter valueType(final ValueType valueType) {
      return this;
    }

    @Override
    public CommandResponseWriter rejectionType(final RejectionType rejectionType) {
      return this;
    }

    @Override
    public CommandResponseWriter rejectionReason(final DirectBuffer rejectionReason) {
      return this;
    }

    @Override
    public CommandResponseWriter valueWriter(final BufferWriter value) {
      return this;
    }

    @Override
    public void tryWriteResponse(final int requestStreamId, final long requestId) {
      // no-op
    }
  }

  /** The system partition does not fan out commands to data partitions. */
  private static final class NoopInterPartitionCommandSender
      implements InterPartitionCommandSender {
    @Override
    public void sendCommand(
        final int receiverPartitionId,
        final ValueType valueType,
        final Intent intent,
        final Long recordKey,
        final UnifiedRecordValue command,
        final AuthInfo authInfo) {
      // no-op
    }
  }
}
