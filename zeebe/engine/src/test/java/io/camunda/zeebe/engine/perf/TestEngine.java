/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.perf;

import io.camunda.search.clients.SearchClientsProxy;
import io.camunda.secretstore.SecretStoreRegistry;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.security.configuration.EngineSecurityConfigurations;
import io.camunda.zeebe.engine.processing.EngineProcessors;
import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.processing.streamprocessor.JobStreamer;
import io.camunda.zeebe.engine.state.DefaultZeebeDbFactory;
import io.camunda.zeebe.engine.util.ProcessingExporterTransistor;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.engine.util.StreamProcessingComposite;
import io.camunda.zeebe.engine.util.TestInterPartitionCommandSender;
import io.camunda.zeebe.engine.util.TestStreams;
import io.camunda.zeebe.engine.util.client.DeploymentClient;
import io.camunda.zeebe.engine.util.client.JobActivationClient;
import io.camunda.zeebe.engine.util.client.ProcessInstanceClient;
import io.camunda.zeebe.logstreams.log.LogStreamReader;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BufferedCommandIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.BufferedCommandRecordValue;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.clock.ActorClock;
import io.camunda.zeebe.scheduler.clock.DefaultActorClock;
import io.camunda.zeebe.stream.impl.StreamProcessorBuilder;
import io.camunda.zeebe.stream.impl.StreamProcessorMode;
import io.camunda.zeebe.test.util.AutoCloseableRule;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.util.FeatureFlags;
import io.micrometer.core.instrument.Timer;
import java.io.IOException;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.rules.TemporaryFolder;

/** Helper class which should help to make it easy to create an engine for tests. */
public final class TestEngine {

  // performance tests build large states, a small RocksDB memory limit would skew the results
  private static final long LARGE_STATE_MEMORY_BYTES = 512L * 1024 * 1024;

  private final StreamProcessingComposite streamProcessingComposite;
  private final TestStreams testStreams;
  private final int partitionCount;

  private TestEngine(
      final int partitionId,
      final int partitionCount,
      final TestContext testContext,
      final InstantSource clock,
      final Consumer<StreamProcessorBuilder> processorConfiguration,
      final boolean withExporter) {
    this.partitionCount = partitionCount;

    testStreams =
        new TestStreams(
            testContext.temporaryFolder(),
            testContext.autoCloseableRule(),
            testContext.actorScheduler(),
            clock);
    testStreams.withStreamProcessorMode(StreamProcessorMode.PROCESSING);
    // for performance reasons we want to enable batch processing
    testStreams.maxCommandsInBatch(100);

    testContext
        .autoCloseableRule()
        .manage(
            testStreams.createLogStream(
                StreamProcessingComposite.getLogName(partitionId), partitionId));

    streamProcessingComposite =
        new StreamProcessingComposite(
            testStreams,
            partitionId,
            DefaultZeebeDbFactory.defaultFactory(partitionCount, LARGE_STATE_MEMORY_BYTES),
            testContext.actorScheduler());

    final var interPartitionCommandSenders = new ArrayList<TestInterPartitionCommandSender>();
    final var featureFlags = FeatureFlags.createDefaultForTests();

    final var interPartitionCommandSender =
        new TestInterPartitionCommandSender(streamProcessingComposite::newLogStreamWriter);
    interPartitionCommandSenders.add(interPartitionCommandSender);
    testContext
        .autoCloseableRule()
        .manage(
            streamProcessingComposite.startTypedStreamProcessor(
                partitionId,
                (recordProcessorContext) -> {
                  final var processors =
                      EngineProcessors.createEngineProcessors(
                          recordProcessorContext,
                          partitionCount,
                          new SubscriptionCommandSender(partitionId, interPartitionCommandSender),
                          interPartitionCommandSender,
                          featureFlags,
                          JobStreamer.noop(),
                          SearchClientsProxy.noop(),
                          new BrokerRequestAuthorizationConverter(
                              EngineSecurityConfigurations.defaultConfig()),
                          new SecretStoreRegistry(Map.of()));
                  return withExporter
                      ? processors.withListener(
                          new ProcessingExporterTransistor(
                              testStreams.getLogStream(
                                  StreamProcessingComposite.getLogName(partitionId))))
                      : processors;
                },
                Optional.empty(),
                processorConfiguration,
                true));
    interPartitionCommandSenders.forEach(s -> s.initializeWriters(partitionCount));
  }

  public DeploymentClient createDeploymentClient() {
    return new DeploymentClient(streamProcessingComposite, (p) -> p.accept(1), partitionCount);
  }

  public ProcessInstanceClient createProcessInstanceClient() {
    return new ProcessInstanceClient(streamProcessingComposite);
  }

  public JobActivationClient createJobActivationClient() {
    return new JobActivationClient(streamProcessingComposite);
  }

  public void writeRecords(final RecordToWrite... recordsToWrite) {
    streamProcessingComposite.writeBatch(recordsToWrite);
  }

  /**
   * Appends {@code recordsToWrite} in batches of at most {@code chunkSize}. A single {@code
   * writeBatch} of many thousands of records exceeds the log's max append size; splitting keeps
   * each append within bounds while still landing every record.
   */
  public void writeRecordsInChunks(final int chunkSize, final RecordToWrite... recordsToWrite) {
    for (int start = 0; start < recordsToWrite.length; start += chunkSize) {
      final int end = Math.min(start + chunkSize, recordsToWrite.length);
      streamProcessingComposite.writeBatch(Arrays.copyOfRange(recordsToWrite, start, end));
    }
  }

  public static TestEngine createSinglePartitionEngine(final TestContext testContext) {
    return new TestEngine(1, 1, testContext, InstantSource.system(), cfg -> {}, true);
  }

  public static TestEngine createSinglePartitionEngine(
      final TestContext testContext, final InstantSource clock) {
    return new TestEngine(1, 1, testContext, clock, cfg -> {}, true);
  }

  /**
   * An engine without the exporter that feeds the {@code RecordingExporter}. The clients of this
   * class cannot be used with it, because they wait for exported records. Drive it with {@link
   * #commandWriter()}, {@link #awaitProcessed(long)} and {@link #newLogReader()} instead.
   */
  public static TestEngine createSinglePartitionEngineWithoutExporter(
      final TestContext testContext) {
    return new TestEngine(1, 1, testContext, InstantSource.system(), cfg -> {}, false);
  }

  public StreamProcessingComposite commandWriter() {
    return streamProcessingComposite;
  }

  /** Blocks until the stream processor has processed the command at {@code position}. */
  public void awaitProcessed(final long position) {
    final var processor = streamProcessingComposite.getStreamProcessor(1);
    while (processor.getLastProcessedPositionAsync().join() < position) {
      LockSupport.parkNanos(50_000);
    }
  }

  /**
   * Exports the RocksDB properties and statistics and returns all meters without a column family.
   */
  public Map<String, Double> dbMetrics() {
    streamProcessingComposite.exportDbMetrics(1);
    final var values = new TreeMap<String, Double>();
    for (final var meter : streamProcessingComposite.getMeterRegistry(1).getMeters()) {
      if (meter.getId().getTag("columnFamily") == null) {
        final var measurement = meter.measure().iterator().next();
        values.put(meter.getId().getName(), measurement.getValue());
      }
    }
    return values;
  }

  /**
   * Exports the metrics that are tagged with a column family (RocksDB access metrics, when the
   * environment variable {@code ZEEBE_DB_ACCESS_METRICS} is set, and the write-behind counters) and
   * returns their current values by column family name and metric.
   */
  public Map<String, Map<String, Double>> columnFamilyMetrics() {
    streamProcessingComposite.exportDbMetrics(1);
    final var names = new HashMap<String, String>();
    for (final var columnFamily : ZbColumnFamilies.values()) {
      names.put(Integer.toString(columnFamily.getValue()), columnFamily.name());
    }
    final var table = new HashMap<String, Map<String, Double>>();
    for (final var meter : streamProcessingComposite.getMeterRegistry(1).getMeters()) {
      final var id = meter.getId();
      final var columnFamily = id.getTag("columnFamily");
      if (columnFamily == null) {
        continue;
      }
      final var operation = id.getTag("operation");
      final var metric =
          operation != null
              ? "rocksdb." + operation
              : id.getName().replace("zeebe.writebehind.", "");
      final double value =
          meter instanceof final Timer timer
              ? timer.count()
              : meter.measure().iterator().next().getValue();
      table
          .computeIfAbsent(names.getOrDefault(columnFamily, columnFamily), k -> new TreeMap<>())
          .put(metric, value);
    }
    return table;
  }

  /** One line per column family with the change since {@code baseline}, the busiest first. */
  public static List<String> formatColumnFamilyMetrics(
      final Map<String, Map<String, Double>> current,
      final Map<String, Map<String, Double>> baseline) {
    final var diff = new HashMap<String, Map<String, Double>>();
    current.forEach(
        (columnFamily, metrics) ->
            metrics.forEach(
                (metric, value) -> {
                  final double change =
                      value
                          - baseline.getOrDefault(columnFamily, Map.of()).getOrDefault(metric, 0.0);
                  if (change != 0) {
                    diff.computeIfAbsent(columnFamily, k -> new TreeMap<>()).put(metric, change);
                  }
                }));
    return diff.entrySet().stream()
        .sorted(
            Comparator.comparingDouble(
                    (Map.Entry<String, Map<String, Double>> e) ->
                        e.getValue().values().stream().mapToDouble(Double::doubleValue).sum())
                .reversed())
        .map(
            e ->
                e.getKey()
                    + " "
                    + e.getValue().entrySet().stream()
                        .map(m -> "%s=%.0f".formatted(m.getKey(), m.getValue()))
                        .collect(Collectors.joining(" ")))
        .toList();
  }

  public LogStreamReader newLogReader() {
    return testStreams.getLogStream(StreamProcessingComposite.getLogName(1)).newLogStreamReader();
  }

  public void reset() {
    RecordingExporter.reset();
    testStreams.resetLog();
  }

  /**
   * Builds a FEEL list literal {@code =[1,2,...,count]} for a parallel multi-instance collection.
   */
  public static String collectionExpression(final int count) {
    return "=["
        + IntStream.rangeClosed(1, count)
            .mapToObj(Integer::toString)
            .collect(Collectors.joining(","))
        + "]";
  }

  /**
   * Waits until {@code count} {@code REOPEN} commands for the given process instance's message
   * subscriptions have reached {@code BUFFERED}. Suspend closes every open subscription and buffers
   * a {@code REOPEN} once each close's async ack lands, so this trailing work can still be in
   * flight once {@code suspend()} itself returns.
   */
  public static void awaitReopenBuffered(final long processInstanceKey, final int count) {
    RecordingExporter.records()
        .withValueType(ValueType.BUFFERED_COMMAND)
        .withIntent(BufferedCommandIntent.BUFFERED)
        .filter(
            r -> {
              final var buffered = (BufferedCommandRecordValue) r.getValue();
              return buffered.getProcessInstanceKey() == processInstanceKey
                  && buffered.getValueType() == ValueType.PROCESS_MESSAGE_SUBSCRIPTION
                  && buffered.getIntent() == ProcessMessageSubscriptionIntent.REOPEN;
            })
        .limit(count)
        .count();
  }

  public static TestContext createTestContext() throws IOException {
    return createTestContext(new DefaultActorClock());
  }

  public static TestContext createTestContext(final ActorClock clock) throws IOException {
    final var autoCloseableRule = new AutoCloseableRule();
    final var temporaryFolder = new TemporaryFolder();
    temporaryFolder.create();
    final var actorScheduler =
        ActorScheduler.newActorScheduler()
            .setCpuBoundActorThreadCount(1)
            .setIoBoundActorThreadCount(1)
            .setActorClock(clock)
            .build();
    autoCloseableRule.manage(actorScheduler);
    actorScheduler.start();
    return new TestContext(actorScheduler, temporaryFolder, autoCloseableRule);
  }

  /**
   * Containing infrastructure related dependencies which might be shared between TestEngines.
   *
   * @param actorScheduler the scheduler which is used during tests
   * @param temporaryFolder the temporary folder where the log and runtime is written to
   * @param autoCloseableRule a collector of all to managed resources, which should be cleaned up
   *     later
   */
  public record TestContext(
      ActorScheduler actorScheduler,
      TemporaryFolder temporaryFolder,
      AutoCloseableRule autoCloseableRule) {

    /** Closes all managed resources and deletes the temporary folder. */
    public void close() {
      autoCloseableRule.close();
      temporaryFolder.delete();
    }
  }
}
