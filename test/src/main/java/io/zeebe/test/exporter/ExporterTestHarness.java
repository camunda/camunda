/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.test.exporter;

import com.moandjiezana.toml.Toml;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.broker.system.configuration.ExporterCfg;
import io.zeebe.exporter.api.context.Configuration;
import io.zeebe.exporter.api.context.Controller;
import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.spi.Exporter;
import io.zeebe.test.exporter.record.MockRecord;
import io.zeebe.test.exporter.record.MockRecordMetadata;
import io.zeebe.test.exporter.record.MockRecordStream;
import io.zeebe.util.ZbLogger;
import java.io.File;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.slf4j.Logger;

/**
 * ExporterTestHarness provides utilities to write unit tests for concrete implementations of the
 * {@link Exporter} interface, by simulating the lifecycle that it would have on a live broker while
 * allowing callers to control the execution flow.
 */
public class ExporterTestHarness {

  private final Logger logger = new ZbLogger("io.zeebe.broker.exporter");
  private final MockController controller = new MockController();
  private final Exporter exporter;
  private final int partitionId = 0;

  private long position = 1;

  /** @param exporter the exporter to be tested */
  public ExporterTestHarness(Exporter exporter) {
    this.exporter = exporter;
  }

  /**
   * Will configure the exporter with a default empty configuration, an empty arguments map, but the
   * correct given ID.
   *
   * @param id the ID of the exporter
   */
  public void configure(String id) {
    final MockConfiguration<Object> configuration = new MockConfiguration<>();
    configuration.setId(id);

    exporter.configure(newContext(configuration));
  }

  /**
   * Will configure the exporter with the given ID, and parse the input as if it were a partial TOML
   * document; this would allow one to test sample configuration and make sure they're parsed as
   * expected.
   *
   * <p>The given TOML document can be a partial document which contains strictly the exporter
   * definition. For example: <code>
   * [[exporters]]
   * id = "elasticsearch"
   * className = "io.zeebe.exporter.ElasticsearchExporter"
   *
   *   [exporters.args.bulk]
   *   delay = 1
   *   size = 1
   * </code>
   *
   * <p>NOTE: the ID of the exporter in the TOML document MUST match the given ID here to avoid any
   * issues where you would pass a sample TOML document with multiple exporter definitions.
   *
   * @param id id of the exporter
   * @param toml the reference TOML document
   */
  public void configure(String id, InputStream toml) {
    final BrokerCfg config = new Toml().read(toml).to(BrokerCfg.class);
    configure(id, config);
  }

  /**
   * Convenience method for {@link ExporterTestHarness#configure(String, InputStream)} which will
   * transform the file into an {@link InputStream}. See its documentation for more.
   *
   * @param id the exporter ID
   * @param configFile pointer to a TOML configuration file
   */
  public void configure(String id, File configFile) {
    final BrokerCfg config = new Toml().read(configFile).to(BrokerCfg.class);
    configure(id, config);
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
  public <T> void configure(String id, T config) {
    final MockConfiguration<T> configuration = new MockConfiguration<>(config);
    configuration.setId(id);

    exporter.configure(newContext(configuration));
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
  public MockRecord export(MockRecord record) {
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
  public MockRecord export(Consumer<MockRecord> configurator) {
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
  public Stream stream(MockRecord seed) {
    return new Stream(MockRecordStream.generate(seed));
  }

  /**
   * Streams a series of record based on a default record, which the caller can modify before it
   * used as a seed for the stream. For more, see {@link ExporterTestHarness#stream(MockRecord)}.
   *
   * @param configurator a consumer to modify the record, can be null
   * @return a stream of {@link MockRecord}
   */
  public Stream stream(Consumer<MockRecord> configurator) {
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
  public void runScheduledTasks(Duration elapsed) {
    controller.runScheduledTasks(elapsed);
  }

  /** @return underlying mock controller */
  public MockController getController() {
    return controller;
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

  private void configure(String id, BrokerCfg brokerCfg) {
    final Optional<ExporterCfg> config =
        brokerCfg.getExporters().stream().filter(c -> c.getId().equals(id)).findFirst();

    if (config.isPresent()) {
      final MockConfiguration<Object> configuration = new MockConfiguration<>();
      configuration.setId(id);
      configuration.setArguments(config.get().getArgs());
      exporter.configure(newContext(configuration));
    } else {
      throw new IllegalArgumentException(String.format("No exporter with ID %s found", id));
    }
  }

  private <T> MockContext newContext(MockConfiguration<T> configuration) {
    return new MockContext(logger, configuration);
  }

  private MockRecord generateNextRecord() {
    return generateNextRecord(new MockRecord());
  }

  private MockRecord generateNextRecord(MockRecord seed) {
    return ((MockRecord) seed.clone())
        .setMetadata(new MockRecordMetadata().setPartitionId(partitionId))
        .setTimestamp(Instant.now())
        .setPosition(++position);
  }

  public class Stream extends MockRecordStream {

    public Stream(java.util.stream.Stream<MockRecord> wrappedStream) {
      super(wrappedStream);
    }

    /**
     * Short-circuiting method; will export exactly {@param count} records from this stream to the
     * exporter. Should be called as the last method a chain.
     *
     * @param count amount of records to export
     */
    public List<Record> export(int count) {
      return limit(count).map(ExporterTestHarness.this::export).collect(Collectors.toList());
    }
  }
}
