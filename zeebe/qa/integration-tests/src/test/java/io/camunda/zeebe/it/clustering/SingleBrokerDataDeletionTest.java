/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.clustering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.zeebe.broker.Broker;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.DataCfg;
import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.api.context.Context.RecordFilter;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.logstreams.log.LogStreamReader;
import io.camunda.zeebe.logstreams.log.LoggedEvent;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.CopiedRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordAssert;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.stream.impl.records.CopiedRecords;
import java.time.Duration;
import java.util.Collections;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.util.unit.DataSize;

public class SingleBrokerDataDeletionTest {

  private static final Duration SNAPSHOT_PERIOD = Duration.ofMinutes(5);
  private static final DataSize LOG_SEGMENT_SIZE = DataSize.ofKilobytes(8);
  private static final DataSize MAX_MESSAGE_SIZE = DataSize.ofKilobytes(4);
  private static final int PARTITION_ID = 1;

  @Rule
  public final ClusteringRule clusteringRule = new ClusteringRule(1, 1, 1, this::configureBroker);

  @After
  public void cleanUp() {
    ControllableExporter.updatePosition(true);
    ControllableExporter.EXPORTED_RECORDS.set(0);
    ControllableExporter.RECORD_TYPE_FILTER.set(r -> true);
    ControllableExporter.VALUE_TYPE_FILTER.set(r -> true);
  }

  @Test
  public void shouldCompactEvenIfSkippingAllRecordsInitially() {
    // given - an exporter which does not update its own position and filters everything but
    // deployment commands
    final LogStream logStream = clusteringRule.getLogStream(1);
    final LogStreamReader reader = logStream.newLogStreamReader().join();
    ControllableExporter.updatePosition(false);
    ControllableExporter.RECORD_TYPE_FILTER.set(t -> t == RecordType.COMMAND);
    ControllableExporter.VALUE_TYPE_FILTER.set(t -> t == ValueType.DEPLOYMENT);

    // when - filling up the log with messages and NO deployments
    publishEnoughMessagesForCompaction();
    deployDummyProcess();
    await("until at least one record is exported")
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> assertThat(ControllableExporter.EXPORTED_RECORDS).hasValueGreaterThan(0));

    // memorize first position pre compaction to compare later on
    reader.seekToFirstEvent();
    final long firstPositionPreCompaction = reader.getPosition();

    // then - enforce compaction and make sure we have less records than we previously did
    clusteringRule.getClock().addTime(SNAPSHOT_PERIOD);
    clusteringRule.waitForSnapshotAtBroker(clusteringRule.getBroker(0));
    await("until some data before the deployment command was compacted")
        .untilAsserted(
            () -> {
              reader.seekToFirstEvent();
              final long firstPositionPostCompaction = reader.getPosition();
              assertThat(firstPositionPostCompaction).isGreaterThan(firstPositionPreCompaction);
              assertContainsDeploymentCommand(reader);
            });
  }

  @Test
  public void shouldNotCompactUnacknowledgedEventsEvenIfSkipping() {
    // given - an exporter which does not update its own position and only accepts deployment
    // commands
    final LogStream logStream = clusteringRule.getLogStream(PARTITION_ID);
    final LogStreamReader reader = logStream.newLogStreamReader().join();
    ControllableExporter.updatePosition(false);
    ControllableExporter.RECORD_TYPE_FILTER.set(t -> t == RecordType.COMMAND);
    ControllableExporter.VALUE_TYPE_FILTER.set(t -> t == ValueType.DEPLOYMENT);

    // when - filling up the log with messages and a single deployment
    publishEnoughMessagesForCompaction();
    deployDummyProcess();
    publishEnoughMessagesForCompaction();
    await("until at least one record is exported")
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> assertThat(ControllableExporter.EXPORTED_RECORDS).hasValueGreaterThan(0));

    // memorize first position pre compaction to compare later on
    reader.seekToFirstEvent();
    final long firstPositionPreCompaction = reader.getPosition();

    // then - enforce compaction and ensure the accepted deployment is still present on the log
    // after compaction
    clusteringRule.getClock().addTime(SNAPSHOT_PERIOD);
    clusteringRule.waitForSnapshotAtBroker(clusteringRule.getBroker(0));
    await("until some data before the deployment command was compacted")
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              reader.seekToFirstEvent();
              final long firstPositionPostCompaction = reader.getPosition();
              assertThat(firstPositionPostCompaction).isGreaterThan(firstPositionPreCompaction);
              assertContainsDeploymentCommand(reader);
            });
  }

  @Test
  public void shouldNotCompactNotExportedEvents() {
    // given
    final LogStream logStream = clusteringRule.getLogStream(1);
    final LogStreamReader reader = logStream.newLogStreamReader().join();
    final Broker broker = clusteringRule.getBroker(0);
    ControllableExporter.updatePosition(true);

    // when - filling the log with messages (updating the position), then a single deployment
    // command, and more messages (all of which do not update the position)
    publishEnoughMessagesForCompaction();
    ControllableExporter.updatePosition(false);
    deployDummyProcess();
    publishEnoughMessagesForCompaction();

    // then - force compaction and ensure we compacted only things before our sentinel command
    reader.seekToFirstEvent();
    long firstPositionPreCompaction = reader.getPosition();
    clusteringRule.getClock().addTime(SNAPSHOT_PERIOD);
    final var firstSnapshot = clusteringRule.waitForSnapshotAtBroker(broker);
    awaitUntilCompaction(reader, firstPositionPreCompaction);
    assertContainsDeploymentCommand(reader);

    // when - re-enabling updating the position
    ControllableExporter.updatePosition(true);
    publishEnoughMessagesForCompaction();

    // then - ensure we can still compact
    reader.seekToFirstEvent();
    firstPositionPreCompaction = reader.getPosition();
    clusteringRule.getClock().addTime(SNAPSHOT_PERIOD);
    clusteringRule.waitForNewSnapshotAtBroker(broker, firstSnapshot);
    awaitUntilCompaction(reader, firstPositionPreCompaction);
  }

  @Test
  public void shouldCompactWhenExporterHasBeenRemoved() {
    // given - an exporter which updates its position and accepts all records
    final int nodeId = 0;
    LogStreamReader reader = clusteringRule.getLogStream(1).newLogStreamReader().join();
    final Broker broker = clusteringRule.getBroker(nodeId);
    ControllableExporter.updatePosition(true);

    // when - filling the log with messages, and a single deployment command for which we will not
    // update the position
    publishEnoughMessagesForCompaction();
    ControllableExporter.updatePosition(false);
    deployDummyProcess();

    // then - force compaction and ensure we compacted only things before our sentinel command
    reader.seekToFirstEvent();
    final long firstPositionPreCompaction = reader.getPosition();
    clusteringRule.getClock().addTime(SNAPSHOT_PERIOD);
    final var firstSnapshot = clusteringRule.waitForSnapshotAtBroker(broker);
    awaitUntilCompaction(reader, firstPositionPreCompaction);
    assertContainsDeploymentCommand(reader);

    // when - restarting without the exporter
    final var brokerCfg = clusteringRule.getBrokerCfg(nodeId);
    brokerCfg.setExporters(Collections.emptyMap());
    clusteringRule.stopBroker(nodeId);
    clusteringRule.startBroker(nodeId);
    publishEnoughMessagesForCompaction();

    // then - force compaction, and expect the deployment command to have been removed
    reader = clusteringRule.getLogStream(1).newLogStreamReader().join();
    final long newFirstPositionPreCompaction = reader.getPosition();
    clusteringRule.getClock().addTime(SNAPSHOT_PERIOD);
    clusteringRule.waitForNewSnapshotAtBroker(clusteringRule.getBroker(0), firstSnapshot);
    assertThat(newFirstPositionPreCompaction).isGreaterThan(firstPositionPreCompaction);
    awaitUntilCompaction(reader, newFirstPositionPreCompaction);
    assertDoesNotContainDeploymentCommand(reader);
  }

  private void awaitUntilCompaction(
      final LogStreamReader reader, final long firstPositionPreCompaction) {
    await("until some data was compacted")
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              reader.seekToFirstEvent();
              final long firstPositionPostCompaction = reader.getPosition();
              assertThat(firstPositionPostCompaction).isGreaterThan(firstPositionPreCompaction);
            });
  }

  private void deployDummyProcess() {
    clusteringRule
        .getClient()
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess("process").startEvent().done(), "process.bpmn")
        .send()
        .join();
  }

  private void configureBroker(final BrokerCfg brokerCfg) {
    final DataCfg data = brokerCfg.getData();
    data.setSnapshotPeriod(SNAPSHOT_PERIOD);
    data.setLogSegmentSize(LOG_SEGMENT_SIZE);
    data.setLogIndexDensity(5);
    brokerCfg.getNetwork().setMaxMessageSize(MAX_MESSAGE_SIZE);

    final ExporterCfg exporterCfg = new ExporterCfg();
    exporterCfg.setClassName(ControllableExporter.class.getName());
    brokerCfg.setExporters(Collections.singletonMap("snapshot-test-exporter", exporterCfg));
  }

  private void publishEnoughMessagesForCompaction() {
    clusteringRule.fillSegments(2);
  }

  private Stream<Record<?>> newRecordStream(final LogStreamReader reader) {
    final Spliterator<LoggedEvent> spliterator =
        Spliterators.spliteratorUnknownSize(reader, Spliterator.ORDERED);
    return StreamSupport.stream(spliterator, false)
        .map(event -> (CopiedRecord<?>) CopiedRecords.createCopiedRecord(1, event));
  }

  private void assertDoesNotContainDeploymentCommand(final LogStreamReader reader) {
    assertThat(newRecordStream(reader))
        .noneSatisfy(
            r ->
                RecordAssert.assertThat(r)
                    .hasRecordType(RecordType.COMMAND)
                    .hasValueType(ValueType.DEPLOYMENT));
  }

  private void assertContainsDeploymentCommand(final LogStreamReader reader) {
    assertThat(newRecordStream(reader))
        .anySatisfy(
            r ->
                RecordAssert.assertThat(r)
                    .hasRecordType(RecordType.COMMAND)
                    .hasValueType(ValueType.DEPLOYMENT));
  }

  public static class ControllableExporter implements Exporter {
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
      }

      EXPORTED_RECORDS.incrementAndGet();
    }
  }
}
