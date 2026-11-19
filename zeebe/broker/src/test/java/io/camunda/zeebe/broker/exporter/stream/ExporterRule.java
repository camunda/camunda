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
import io.camunda.zeebe.broker.test.TestZeebeDbFactory;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.logstreams.log.WriteContext;
import io.camunda.zeebe.logstreams.util.ListLogStorage;
import io.camunda.zeebe.logstreams.util.TestLogStream;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.impl.record.VersionInfo;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.scheduler.clock.ControlledActorClock;
import io.camunda.zeebe.scheduler.testing.ActorSchedulerRule;
import io.camunda.zeebe.stream.api.EventFilter;
import io.camunda.zeebe.stream.api.StreamClock;
import io.camunda.zeebe.stream.impl.SkipPositionsFilter;
import io.camunda.zeebe.test.util.AutoCloseableRule;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
  private ZeebeDb<ZbColumnFamilies> capturedZeebeDb;

  private TestLogStream logStream;
  private PartitionMessagingService partitionMessagingService = new SimplePartitionMessageService();
  private ExporterDirector director;
  private Duration distributionInterval = Duration.ofSeconds(15);
  private EventFilter positionsToSkipFilter = SkipPositionsFilter.of(Set.of());

  private Consumer<ExporterDirectorContext> contextApplier = c -> {};

  private ExporterRule(final ExporterMode exporterMode) {
    this.exporterMode = exporterMode;
    final SetupRule rule = new SetupRule(PARTITION_ID);

    zeebeDbFactory = TestZeebeDbFactory.defaultFactory();
    chain =
        RuleChain.outerRule(tempFolder).around(actorSchedulerRule).around(closeables).around(rule);
  }

  public static ExporterRule activeExporter() {
    return new ExporterRule(ExporterMode.ACTIVE);
  }

  public static ExporterRule passiveExporter() {
    return new ExporterRule(ExporterMode.PASSIVE);
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
    final var runtimeFolder = createRuntimeFolder();
    capturedZeebeDb = spy(zeebeDbFactory.createDb(runtimeFolder.toFile(), false));

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
            .logStream(logStream)
            .clock(StreamClock.system())
            .zeebeDb(capturedZeebeDb)
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
    return append(newMetadata(recordType, intent, value), value);
  }

  /** Writes an event as though it had been written by a broker running {@code brokerVersion}. */
  public long writeEventWithBrokerVersion(
      final Intent intent, final UnifiedRecordValue value, final VersionInfo brokerVersion) {
    return append(newMetadata(RecordType.EVENT, intent, value).brokerVersion(brokerVersion), value);
  }

  private static RecordMetadata newMetadata(
      final RecordType recordType, final Intent intent, final UnifiedRecordValue value) {
    return new RecordMetadata()
        .protocolVersion(Protocol.PROTOCOL_VERSION)
        .recordType(recordType)
        .intent(intent)
        .valueType(value.valueType());
  }

  private long append(final RecordMetadata metadata, final UnifiedRecordValue value) {
    return logStream
        .newBlockingLogStreamWriter()
        .tryWrite(WriteContext.internal(), LogAppendEntry.of(metadata, value))
        .get();
  }

  private Path createRuntimeFolder() {
    final var rootDirectory =
        tempFolder.getRoot().toPath().resolve(logStream.getLogName()).resolve("state");
    try {
      Files.createDirectories(rootDirectory);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
    return rootDirectory.resolve("runtime");
  }

  public void closeExporterDirector() throws Exception {
    director.stopAsync().join();
    capturedZeebeDb.close();
    capturedZeebeDb = null;
  }

  private class SetupRule extends ExternalResource {

    private final int partitionId;

    SetupRule(final int partitionId) {
      this.partitionId = partitionId;
    }

    @Override
    protected void before() {
      final var logStorage = new ListLogStorage();
      final var meterRegistry = new SimpleMeterRegistry();
      logStream =
          TestLogStream.builder()
              .withLogName(STREAM_NAME)
              .withLogStorage(logStorage)
              .withPartitionId(partitionId)
              .withClock(clock)
              .withMeterRegistry(meterRegistry)
              .build();
      logStorage.setPositionListener(logStream::setLastWrittenPosition);
      closeables.manage(logStream);
      closeables.manage(() -> MicrometerUtil.close(meterRegistry));
    }
  }
}
