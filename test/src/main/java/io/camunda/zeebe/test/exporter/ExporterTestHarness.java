/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.exporter;

import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.context.Configuration;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.test.exporter.record.MockRecord;
import io.camunda.zeebe.test.exporter.record.MockRecordMetadata;
import io.camunda.zeebe.test.exporter.record.MockRecordStream;
import io.camunda.zeebe.test.util.TestConfigurationFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ExporterTestHarness provides utilities to write unit tests for concrete implementations of the
 * {@link Exporter} interface, by simulating the lifecycle that it would have on a live broker while
 * allowing callers to control the execution flow.
 *
 * @deprecated since 1.3.0. See issue <a
 *     href="https://github.com/camunda-cloud/zeebe/issues/8143">8143</a> for more information.
 */
@Deprecated(since = "1.3.0", forRemoval = true)
public class ExporterTestHarness {

  private final Logger logger = LoggerFactory.getLogger("io.camunda.zeebe.broker.exporter");
  private final MockController controller = new MockController();
  private final Exporter exporter;
  private final int partitionId = 0;

  private MockContext context;
  private long position = 1;

  /** @param exporter the exporter to be tested */
  public ExporterTestHarness(final Exporter exporter) {
    this.exporter = exporter;
  }

  /**
   * Will configure the exporter with a default empty configuration, an empty arguments map, but the
   * correct given ID.
   *
   * @param id the ID of the exporter
   */
  public void configure(final String id) throws Exception {
    final MockConfiguration<Object> configuration = new MockConfiguration<>();
    configuration.setId(id);

    exporter.configure(newContext(configuration));
  }

  /**
   * Will configure the exporter with the given ID, and parse the input as if it were a partial YAML
   * document; this would allow one to test sample configuration and make sure they're parsed as
   * expected.
   *
   * <p>The given YAML document can be a partial document which contains strictly the exporter
   * definition. For example: <code>
   * zeebe:
   *   broker:
   *     exporters:
   *       elasticsearsch:
   *         className: io.camunda.zeebe.exporter.ElasticsearchExporter
   *         args:
   *         ...
   * </code>
   *
   * <p>NOTE: the ID of the exporter in the YAML document MUST match the given ID here to avoid any
   * issues where you would pass a sample YAML document with multiple exporter definitions.
   *
   * @param id id of the exporter
   * @param yaml the reference YAML document
   */
  public void configure(final String id, final InputStream yaml) throws Exception {
    final BrokerCfg config = new TestConfigurationFactory().create(yaml, BrokerCfg.class);
    configure(id, config);
  }

  /**
   * Convenience method for {@link ExporterTestHarness#configure(String, InputStream)} which will
   * transform the file into an {@link InputStream}. See its documentation for more.
   *
   * @param id the exporter ID
   * @param configFile pointer to a yaml configuration file
   */
  public void configure(final String id, final File configFile) throws Exception {
    try (final InputStream configStream = new FileInputStream(configFile)) {
      configure(id, configStream);
    }
  }

  /**
   * Convenience method which will substitute a prepared configuration instance such that it is
   * always returned by a call to {@link Configuration#instantiate(Class)}
   *
   * <p>NOTE: in this case, {@link Configuration#getArguments()} always returns an empty map.
   *
   * @param id the exporter ID
   * @param config new return value of {@link Configuration#instantiate(Class)}
   * @param <T> type of the configuration class
   */
  public <T> void configure(final String id, final T config) throws Exception {
    final MockConfiguration<T> configuration = new MockConfiguration<>(config);
    configuration.setId(id);

    context = newContext(configuration);
    exporter.configure(context);
  }

  /**
   * Resets scheduled tasks and opens the exporter.
   *
   * <p>NOTE: if closing/opening the exporter here several times, the start position will be the
   * same as the end position when the exporter was last closed.
   */
  public void open() {
    controller.resetScheduler();
    exporter.open(controller);
  }

  /** Closes the exporter */
  public void close() {
    exporter.close();
  }

  /**
   * Exports a single default record.
   *
   * @return generated record
   */
  public MockRecord export() {
    final MockRecord record = generateNextRecord();
    return export(record);
  }

  /**
   * Exports the given record, updating the latest position to the position of the record.
   *
   * @param record record to export
   * @return exported record
   */
  public MockRecord export(final MockRecord record) {
    exporter.export(record);
    position = record.getPosition();

    return record;
  }

  /**
   * Will export a mock record to the exporter; the {@param configurator} is called right before,
   * providing a means of modifying the record before.
   *
   * @param configurator a consumer to modify the record, can be null
   * @return the exported record
   */
  public MockRecord export(final Consumer<MockRecord> configurator) {
    final MockRecord record = generateNextRecord();

    if (configurator != null) {
      configurator.accept(record);
    }

    return export(record);
  }

  /**
   * Streams a series of record.
   *
   * @return a stream of {@link MockRecord}
   */
  public Stream stream() {
    return new Stream(MockRecordStream.generate());
  }

  /**
   * Streams a series of record based on the given seed.
   *
   * <p>Subsequent records after the seed will be exactly the same, with the exception that the
   * position and timestamps of later records are <em>always</em> greater than that of previous
   * ones.
   *
   * @param seed the initial sample record
   * @return a stream of {@link MockRecord}
   */
  public Stream stream(final MockRecord seed) {
    return new Stream(MockRecordStream.generate(seed));
  }

  /**
   * Streams a series of record based on a default record, which the caller can modify before it
   * used as a seed for the stream. For more, see {@link ExporterTestHarness#stream(MockRecord)}.
   *
   * @param configurator a consumer to modify the record, can be null
   * @return a stream of {@link MockRecord}
   */
  public Stream stream(final Consumer<MockRecord> configurator) {
    final MockRecord seed = generateNextRecord();
    if (configurator != null) {
      configurator.accept(seed);
    }

    return stream(seed);
  }

  /**
   * Runs all scheduled tasks which should be expired if {@param elapsed} time has passed since the
   * last time this was called.
   *
   * @param elapsed time to elapse
   */
  public void runScheduledTasks(final Duration elapsed) {
    controller.runScheduledTasks(elapsed);
  }

  /** @return underlying mock controller */
  public MockController getController() {
    return controller;
  }

  public MockContext getContext() {
    return context;
  }

  /**
   * Returns the last exported record's position; note that this is <em>not</em> the last updated
   * record position, as updated by the exporter, but simply the position of that last record handed
   * off to the exporter via {@link Exporter#export(Record)}.
   *
   * @return the last exported record's position
   */
  public long getPosition() {
    return position;
  }

  /**
   * Returns the last position as reported by the exporter through {@link
   * Controller#updateLastExportedRecordPosition(long)}
   *
   * @return the last exported record position
   */
  public long getLastUpdatedPosition() {
    return controller.getPosition();
  }

  private void configure(final String id, final BrokerCfg brokerCfg) throws Exception {
    final Optional<ExporterCfg> config = Optional.ofNullable(brokerCfg.getExporters().get(id));

    if (config.isPresent()) {
      final MockConfiguration<Object> configuration = new MockConfiguration<>();
      configuration.setId(id);
      configuration.setArguments(config.get().getArgs());
      context = newContext(configuration);
      exporter.configure(context);
    } else {
      throw new IllegalArgumentException(String.format("No exporter with ID %s found", id));
    }
  }

  private <T> MockContext newContext(final MockConfiguration<T> configuration) {
    return new MockContext(logger, configuration);
  }

  private MockRecord generateNextRecord() {
    return generateNextRecord(new MockRecord());
  }

  private MockRecord generateNextRecord(final MockRecord seed) {
    return ((MockRecord) seed.clone())
        .setMetadata(new MockRecordMetadata().setPartitionId(partitionId))
        .setTimestamp(System.currentTimeMillis())
        .setPosition(++position);
  }

  public class Stream extends MockRecordStream {

    public Stream(final java.util.stream.Stream<MockRecord> wrappedStream) {
      super(wrappedStream);
    }

    /**
     * Short-circuiting method; will export exactly {@param count} records from this stream to the
     * exporter. Should be called as the last method a chain.
     *
     * @param count amount of records to export
     */
    public List<Record> export(final int count) {
      return limit(count).map(ExporterTestHarness.this::export).collect(Collectors.toList());
    }
  }
}
