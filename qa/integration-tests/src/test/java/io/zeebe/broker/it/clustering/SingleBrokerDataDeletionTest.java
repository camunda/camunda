/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.it.clustering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.zeebe.broker.Broker;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.broker.system.configuration.DataCfg;
import io.zeebe.broker.system.configuration.ExporterCfg;
import io.zeebe.exporter.api.Exporter;
import io.zeebe.exporter.api.context.Context;
import io.zeebe.exporter.api.context.Context.RecordFilter;
import io.zeebe.exporter.api.context.Controller;
import io.zeebe.logstreams.log.LogStreamReader;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.ValueType;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.util.unit.DataSize;

public class SingleBrokerDataDeletionTest {

  private static final Duration SNAPSHOT_PERIOD = Duration.ofMinutes(5);
  private static final int SEGMENT_COUNT = 5;

  @Rule
  public final ClusteringRule clusteringRule =
      new ClusteringRule(1, 1, 1, this::configureCustomExporter);

  private final AtomicLong writtenRecords = new AtomicLong(0);

  private void configureCustomExporter(final BrokerCfg brokerCfg) {
    final DataCfg data = brokerCfg.getData();
    data.setSnapshotPeriod(SNAPSHOT_PERIOD);
    data.setLogSegmentSize(DataSize.ofKilobytes(8));
    data.setLogIndexDensity(5);
    brokerCfg.getNetwork().setMaxMessageSize(DataSize.ofKilobytes(8));

    final ExporterCfg exporterCfg = new ExporterCfg();
    exporterCfg.setClassName(ControllableExporter.class.getName());
    brokerCfg.setExporters(Collections.singletonMap("snapshot-test-exporter", exporterCfg));
  }

  @Test
  public void shouldCompactEvenIfSkippingAllRecords() {
    // given
    final Broker broker = clusteringRule.getBroker(0);

    // when
    ControllableExporter.updatePosition(false);
    ControllableExporter.RECORD_TYPE_FILTER.set(r -> r == RecordType.COMMAND);
    ControllableExporter.VALUE_TYPE_FILTER.set(r -> r == ValueType.DEPLOYMENT);
    writeSegments(broker, 2);
    clusteringRule
        .getClient()
        .newDeployCommand()
        .addWorkflowModel(
            Bpmn.createExecutableProcess("process").startEvent().done(), "process.bpmn")
        .send()
        .join();
    await("until at least one record is exported")
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> assertThat(ControllableExporter.EXPORTED_RECORDS).hasValueGreaterThan(0));

    // enforce compaction
    final var segmentsBeforeSnapshot = getSegmentsCount(broker);
    clusteringRule.getClock().addTime(SNAPSHOT_PERIOD);

    // then
    assertThat(clusteringRule.waitForSnapshotAtBroker(broker)).isNotNull();
    await()
        .untilAsserted(
            () ->
                assertThat(getSegmentsCount(broker))
                    .describedAs("Expected less segments after a snapshot is taken")
                    .isLessThan(segmentsBeforeSnapshot));
  }

  @Test
  public void shouldNotCompactUnacknowledgedEventsEvenIfSkipping() {
    // given
    final RecordMetadata metadata = new RecordMetadata();
    final Broker broker = clusteringRule.getBroker(0);
    final var logstream = clusteringRule.getLogStream(1);
    final var reader = logstream.newLogStreamReader().join();

    // when
    ControllableExporter.updatePosition(false);
    ControllableExporter.RECORD_TYPE_FILTER.set(r -> r == RecordType.COMMAND);
    ControllableExporter.VALUE_TYPE_FILTER.set(r -> r == ValueType.DEPLOYMENT);
    writeSegments(broker, 2);
    clusteringRule
        .getClient()
        .newDeployCommand()
        .addWorkflowModel(
            Bpmn.createExecutableProcess("process").startEvent().done(), "process.bpmn")
        .send()
        .join();
    writeSegments(broker, 2);
    await("until at least one record is exported")
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> assertThat(ControllableExporter.EXPORTED_RECORDS).hasValueGreaterThan(0));

    // grab the first log position, and the position of the last unacknowledged event
    reader.seekToFirstEvent();
    final long firstPosition = reader.getPosition();
    long lastUnacknowledgedPosition = -1;
    while (reader.hasNext()) {
      final LoggedEvent event = reader.next();
      event.readMetadata(metadata);
      if (metadata.getValueType() == ValueType.DEPLOYMENT) {
        lastUnacknowledgedPosition = event.getPosition();
        break;
      }
    }

    // enforce compaction
    final var segmentsBeforeSnapshot = getSegmentsCount(broker);
    clusteringRule.getClock().addTime(SNAPSHOT_PERIOD);

    // then
    assertThat(lastUnacknowledgedPosition).isGreaterThan(-1L);
    assertThat(clusteringRule.waitForSnapshotAtBroker(broker)).isNotNull();
    await()
        .untilAsserted(
            () ->
                assertThat(getSegmentsCount(broker))
                    .describedAs("Expected less segments after a snapshot is taken")
                    .isLessThan(segmentsBeforeSnapshot));
    reader.seekToFirstEvent();
    assertThat(reader.getPosition())
        .isGreaterThan(firstPosition)
        .isLessThanOrEqualTo(lastUnacknowledgedPosition);
  }

  @Test
  public void shouldNotCompactNotExportedEvents() {
    // given
    final Broker broker = clusteringRule.getBroker(0);

    final var logstream = clusteringRule.getLogStream(1);
    final var reader = logstream.newLogStreamReader().join();

    // - write records and update the exporter position
    ControllableExporter.updatePosition(true);
    fillSegments(broker, SEGMENT_COUNT);

    // - write more records but don't update the exporter position
    ControllableExporter.updatePosition(false);

    final var filledSegmentCount = SEGMENT_COUNT * 2;
    fillSegments(broker, filledSegmentCount);

    // - trigger a snapshot creation
    clusteringRule.getClock().addTime(SNAPSHOT_PERIOD);
    final var firstSnapshot = clusteringRule.waitForSnapshotAtBroker(broker);

    await()
        .untilAsserted(
            () ->
                assertThat(getSegmentsCount(broker))
                    .describedAs("Expected less segments after a snapshot is taken")
                    .isLessThan(filledSegmentCount));

    // then verify that the log still contains the records that are not exported
    final var firstNonExportedPosition =
        ControllableExporter.NOT_EXPORTED_RECORDS.get(0).getPosition();

    assertThat(hasRecordWithPosition(reader, firstNonExportedPosition))
        .describedAs("Expected first non-exported record to be present in the log but not found.")
        .isTrue();

    // - write more records and update the exporter position again
    final var segmentsBeforeSnapshot = getSegmentsCount(broker);

    ControllableExporter.updatePosition(true);
    fillSegments(broker, segmentsBeforeSnapshot + 1);

    // - trigger the next snapshot creation
    clusteringRule.getClock().addTime(SNAPSHOT_PERIOD);
    clusteringRule.waitForNewSnapshotAtBroker(broker, firstSnapshot);

    // then verify that the log is now compacted after the exporter position was updated
    await()
        .untilAsserted(
            () ->
                assertThat(getSegmentsCount(broker))
                    .describedAs("Expected less segments after a snapshot is taken")
                    .isLessThan(segmentsBeforeSnapshot));
  }

  @Test
  public void shouldCompactWhenExporterHasBeenRemoved() {
    // given
    final Broker broker = clusteringRule.getBroker(0);
    ControllableExporter.updatePosition(true);
    fillSegments(broker, SEGMENT_COUNT);
    clusteringRule.getClock().addTime(SNAPSHOT_PERIOD);
    // create first snapshot with exporter positions
    final var firstSnapshot = clusteringRule.waitForSnapshotAtBroker(broker);

    // restart with no exporter
    final var brokerCfg = clusteringRule.getBrokerCfg(0);
    brokerCfg.setExporters(Map.of());
    clusteringRule.stopBroker(0);
    clusteringRule.startBroker(0);

    final var filledSegmentCount = SEGMENT_COUNT * 2;
    writeSegments(broker, filledSegmentCount);

    // when triggering new snapshot creation
    final var segmentsCount = getSegmentsCount(broker);
    clusteringRule.getClock().addTime(SNAPSHOT_PERIOD);
    final var secondSnapshot = clusteringRule.waitForNewSnapshotAtBroker(broker, firstSnapshot);

    // then
    assertThat(firstSnapshot).isNotEqualTo(secondSnapshot);
    await()
        .untilAsserted(
            () ->
                assertThat(getSegmentsCount(broker))
                    .describedAs("Expected less segments after a snapshot is taken")
                    .isLessThan(segmentsCount));
  }

  private void fillSegments(final Broker broker, final int segmentCount) {
    writeSegments(broker, segmentCount);

    await()
        .untilAsserted(
            () ->
                assertThat(ControllableExporter.EXPORTED_RECORDS.get())
                    .describedAs("Expected all written records to be exported")
                    .isGreaterThanOrEqualTo(writtenRecords.get()));
  }

  private void writeSegments(final Broker broker, final int segmentCount) {
    while (getSegmentsCount(broker) <= segmentCount) {
      writeToLog();
      writtenRecords.incrementAndGet();
    }
  }

  private void writeToLog() {

    clusteringRule
        .getClient()
        .newPublishMessageCommand()
        .messageName("msg")
        .correlationKey("key")
        .send()
        .join();
  }

  private int getSegmentsCount(final Broker broker) {
    return getSegments(broker).size();
  }

  private Collection<Path> getSegments(final Broker broker) {
    try {
      return Files.list(clusteringRule.getSegmentsDirectory(broker))
          .filter(path -> path.toString().endsWith(".log"))
          .collect(Collectors.toList());
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private boolean hasRecordWithPosition(final LogStreamReader reader, final long recordPosition) {
    await()
        .until(
            () -> {
              try {
                reader.seek(recordPosition);
                return reader.hasNext();

              } catch (final Exception ignore) {
                // may fail if the compaction is not completed yet
                return false;
              }
            });

    final var readerPosition = reader.next().getPosition();
    return readerPosition == recordPosition;
  }

  @After
  public void cleanUp() {
    ControllableExporter.NOT_EXPORTED_RECORDS.clear();
    ControllableExporter.updatePosition(true);
    ControllableExporter.EXPORTED_RECORDS.set(0);
    ControllableExporter.RECORD_TYPE_FILTER.set(r -> true);
    ControllableExporter.VALUE_TYPE_FILTER.set(r -> true);

    writtenRecords.set(0);
  }

  public static class ControllableExporter implements Exporter {
    static final List<Record<?>> NOT_EXPORTED_RECORDS = new CopyOnWriteArrayList<>();
    static volatile boolean shouldExport = true;

    static final AtomicLong EXPORTED_RECORDS = new AtomicLong(0);
    static final AtomicReference<Predicate<RecordType>> RECORD_TYPE_FILTER =
        new AtomicReference<>(r -> true);
    static final AtomicReference<Predicate<ValueType>> VALUE_TYPE_FILTER =
        new AtomicReference<>(r -> true);

    private Controller controller;

    static void updatePosition(final boolean flag) {
      shouldExport = flag;
    }

    @Override
    public void configure(final Context context) {
      context.setFilter(
          new RecordFilter() {
            @Override
            public boolean acceptType(final RecordType recordType) {
              return RECORD_TYPE_FILTER.get().test(recordType);
            }

            @Override
            public boolean acceptValue(final ValueType valueType) {
              return VALUE_TYPE_FILTER.get().test(valueType);
            }
          });
    }

    @Override
    public void open(final Controller controller) {
      this.controller = controller;
    }

    @Override
    public void export(final Record<?> record) {
      if (shouldExport) {
        controller.updateLastExportedRecordPosition(record.getPosition());
      } else {
        NOT_EXPORTED_RECORDS.add(record);
      }

      EXPORTED_RECORDS.incrementAndGet();
    }
  }
}
