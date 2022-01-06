/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.exporter;

import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.context.Configuration;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.test.exporter.record.MockRecord;
import io.camunda.zeebe.test.exporter.record.MockRecordMetadata;
import io.camunda.zeebe.test.exporter.record.MockRecordStream;
import java.util.List;
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

  /** @return underlying mock controller */
  public MockController getController() {
    return controller;
  }

  public MockContext getContext() {
    return context;
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
