/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.stream;

import static org.mockito.Mockito.spy;

import io.camunda.cluster.PartitionId;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.zeebe.broker.exporter.repo.ExporterDescriptor;
import io.camunda.zeebe.broker.exporter.stream.ExporterDirector.ExporterInitializationInfo;
import io.camunda.zeebe.broker.exporter.stream.ExporterDirectorContext.ExporterMode;
import io.camunda.zeebe.broker.system.partitions.PartitionMessagingService;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.db.layered.zdb.LayeredZeebeDbConfig;
import io.camunda.zeebe.db.layered.zdb.LayeredZeebeDbFactory;
import io.camunda.zeebe.engine.state.DefaultZeebeDbFactory;
import io.camunda.zeebe.engine.util.TestStreams;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.scheduler.clock.ControlledActorClock;
import io.camunda.zeebe.scheduler.testing.ActorSchedulerRule;
import io.camunda.zeebe.stream.api.EventFilter;
import io.camunda.zeebe.stream.api.StreamClock;
import io.camunda.zeebe.stream.impl.SkipPositionsFilter;
import io.camunda.zeebe.test.util.AutoCloseableRule;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.rules.ExternalResource;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public final class ExporterRule implements TestRule {

  private static final int PARTITION_ID = 1;
  private static final int EXPORTER_PROCESSOR_ID = 101;
  private static final String PROCESSOR_NAME = "exporter";
  private static final String STREAM_NAME = "stream";

  // environment
  private final TemporaryFolder tempFolder = new TemporaryFolder();
  private final AutoCloseableRule closeables = new AutoCloseableRule();
  private final ControlledActorClock clock = new ControlledActorClock();
  private final ActorSchedulerRule actorSchedulerRule = new ActorSchedulerRule(clock);
  private final RuleChain chain;

  private final ZeebeDbFactory zeebeDbFactory;
  private final ExporterMode exporterMode;
  private LayeredZeebeDbConfig layeredDbConfig;
  private ZeebeDb<ZbColumnFamilies> capturedZeebeDb;

  private TestStreams streams;
  private PartitionMessagingService partitionMessagingService = new SimplePartitionMessageService();
  private ExporterDirector director;
  private Duration distributionInterval = Duration.ofSeconds(15);
  private EventFilter positionsToSkipFilter = SkipPositionsFilter.of(Set.of());

  private Consumer<ExporterDirectorContext> contextApplier = c -> {};

  private ExporterRule(final ExporterMode exporterMode) {
    this.exporterMode = exporterMode;
    final SetupRule rule = new SetupRule(PARTITION_ID);

    zeebeDbFactory = DefaultZeebeDbFactory.defaultFactory();
    chain =
        RuleChain.outerRule(tempFolder).around(actorSchedulerRule).around(closeables).around(rule);
  }

  public static ExporterRule activeExporter() {
    return new ExporterRule(ExporterMode.ACTIVE);
  }

  public static ExporterRule passiveExporter() {
    return new ExporterRule(ExporterMode.PASSIVE);
  }

  /**
   * Wraps the runtime database in a {@code LayeredZeebeDb} (the experimental layered-state flag's
   * wiring), so the director writes exporter positions through the exporter ownership domain and
   * drains them at the given config's persist cadence.
   */
  public ExporterRule withLayeredDb(final LayeredZeebeDbConfig config) {
    layeredDbConfig = config;
    return this;
  }

  public ExporterRule withPartitionMessageService(
      final PartitionMessagingService partitionMessagingService) {
    this.partitionMessagingService = partitionMessagingService;
    return this;
  }

  public ExporterRule withDistributionInterval(final Duration distributionInterval) {
    this.distributionInterval = distributionInterval;
    return this;
  }

  public ExporterRule withPositionsToSkipFilter(final EventFilter positionsToSkipFilter) {
    this.positionsToSkipFilter = positionsToSkipFilter;
    return this;
  }

  public ExporterRule withExporterDirectorContextConfigurator(
      final Consumer<ExporterDirectorContext> contextApplier) {
    this.contextApplier = contextApplier;
    return this;
  }

  @Override
  public Statement apply(final Statement base, final Description description) {
    return chain.apply(base, description);
  }

  public void startExporterDirector(final List<ExporterDescriptor> exporterDescriptors) {
    startExporterDirector(exporterDescriptors, ExporterPhase.EXPORTING);
  }

  public void startExporterDirector(
      final List<ExporterDescriptor> exporterDescriptors, final ExporterPhase phase) {
    startExporterDirector(exporterDescriptors, phase, Function.identity());
  }

  public void startExporterDirector(
      final List<ExporterDescriptor> exporterDescriptors,
      final ExporterPhase phase,
      final Function<RecordExporter, RecordExporter> recordExporter) {
    final var stream = streams.getLogStream(STREAM_NAME);
    final var runtimeFolder = streams.createRuntimeFolder(stream);
    capturedZeebeDb = createRuntimeDb(runtimeFolder.toFile());
    startExporterDirectorOnDb(exporterDescriptors, phase, recordExporter, capturedZeebeDb);
  }

  /**
   * Starts a new director on the database of the previous one — a leader transition on a reused
   * runtime database — without recreating the database. The previous director must be stopped first
   * (see {@link #stopExporterDirector()}).
   */
  public void startExporterDirectorOnSameDb(final List<ExporterDescriptor> exporterDescriptors) {
    startExporterDirectorOnDb(
        exporterDescriptors, ExporterPhase.EXPORTING, Function.identity(), capturedZeebeDb);
  }

  @SuppressWarnings("unchecked")
  private ZeebeDb<ZbColumnFamilies> createRuntimeDb(final File runtimeFolder) {
    if (layeredDbConfig != null) {
      // no spy: the layered decorator is final, and the layered tests assert on behavior
      return LayeredZeebeDbFactory.of(
              (ZeebeDbFactory<ZbColumnFamilies>) zeebeDbFactory,
              layeredDbConfig,
              ZbColumnFamilies.class)
          .createDb(runtimeFolder, false);
    }
    return spy((ZeebeDb<ZbColumnFamilies>) zeebeDbFactory.createDb(runtimeFolder, false));
  }

  private void startExporterDirectorOnDb(
      final List<ExporterDescriptor> exporterDescriptors,
      final ExporterPhase phase,
      final Function<RecordExporter, RecordExporter> recordExporter,
      final ZeebeDb<ZbColumnFamilies> zeebeDb) {
    final var stream = streams.getLogStream(STREAM_NAME);

    final var descriptorsWithInitializationInfo =
        exporterDescriptors.stream()
            .collect(
                Collectors.toMap(
                    descriptor -> descriptor,
                    descriptor -> new ExporterInitializationInfo(0, null)));

    final ExporterDirectorContext context =
        new ExporterDirectorContext()
            .partitionId(
                new PartitionId(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, PARTITION_ID))
            .id(EXPORTER_PROCESSOR_ID)
            .logStream(stream)
            .clock(StreamClock.system())
            .zeebeDb(zeebeDb)
            .exporterMode(exporterMode)
            .distributionInterval(distributionInterval)
            .partitionMessagingService(partitionMessagingService)
            .descriptors(descriptorsWithInitializationInfo)
            .meterRegistry(new SimpleMeterRegistry())
            .positionsToSkipFilter(positionsToSkipFilter)
            .tenantName(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID);

    contextApplier.accept(context);
    director = new ExporterDirector(context, phase, recordExporter);
    director.startAsync(actorSchedulerRule.get()).join();
  }

  public ExporterDirector getDirector() {
    return director;
  }

  public ControlledActorClock getClock() {
    return clock;
  }

  public ExportersState getExportersState() {
    if (capturedZeebeDb == null) {
      throw new IllegalStateException(
          "Exporter director has to be started before accessing the database.");
    }
    return new ExportersState(capturedZeebeDb, capturedZeebeDb.createContext());
  }

  public long writeEvent(final Intent intent, final UnifiedRecordValue value) {
    return writeRecord(RecordType.EVENT, intent, value);
  }

  public long writeCommand(final Intent intent, final UnifiedRecordValue value) {
    return writeRecord(RecordType.COMMAND, intent, value);
  }

  public long writeRecord(
      final RecordType recordType, final Intent intent, final UnifiedRecordValue value) {
    return streams
        .newRecord(STREAM_NAME)
        .recordType(recordType)
        .intent(intent)
        .event(value)
        .write();
  }

  public void closeExporterDirector() throws Exception {
    director.stopAsync().join();
    capturedZeebeDb.close();
    capturedZeebeDb = null;
  }

  /** Stops the director but keeps the database open, so a successor can take it over. */
  public void stopExporterDirector() {
    director.stopAsync().join();
  }

  private class SetupRule extends ExternalResource {

    private final int partitionId;

    SetupRule(final int partitionId) {
      this.partitionId = partitionId;
    }

    @Override
    protected void before() {
      streams = new TestStreams(tempFolder, closeables, actorSchedulerRule.get(), clock);
      streams.createLogStream(STREAM_NAME, partitionId);
    }
  }
}
