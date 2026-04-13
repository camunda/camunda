/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.kafka;

import io.camunda.exporter.kafka.config.Config;
import io.camunda.exporter.kafka.config.ConfigParser;
import io.camunda.exporter.kafka.config.RawConfig;
import io.camunda.exporter.kafka.producer.KafkaRecordPublisher;
import io.camunda.exporter.kafka.record.KafkaRecordFilter;
import io.camunda.exporter.kafka.record.RecordHandler;
import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.exporter.api.context.ScheduledTask;
import io.camunda.zeebe.protocol.record.Record;
import org.slf4j.Logger;

/**
 * Exports Zeebe records to Kafka topics with configurable topic routing and batching.
 *
 * <p>The publisher runs its flush loop on a dedicated background thread so that Kafka I/O never
 * blocks the Zeebe actor thread. The actor thread calls {@link #export} to enqueue records (fast,
 * no I/O) and a periodic scheduled task reads {@link KafkaRecordPublisher#getLastFlushedPosition()}
 * to acknowledge the export position with the Zeebe controller.
 *
 * <p>Record-type filtering is delegated to {@link KafkaRecordFilter}, which is registered with the
 * Zeebe context and runs upstream of {@link #export}. The exporter itself only enforces the
 * partition-1 scope for definition and identity value types.
 */
public final class CamundaKafkaExporter implements Exporter {

  private Logger logger;
  private Config config;
  private int partitionId;
  private Controller controller;
  private RecordHandler recordHandler;
  private KafkaRecordPublisher publisher;
  private ScheduledTask positionUpdateTask;
  private long lastReportedPosition = -1;

  @Override
  public void configure(final Context context) {
    logger = context.getLogger();
    partitionId = context.getPartitionId();
    final RawConfig rawConfig = context.getConfiguration().instantiate(RawConfig.class);
    config = ConfigParser.parse(rawConfig);
    recordHandler = new RecordHandler(config.records());
    context.setFilter(new KafkaRecordFilter(config.records()));
  }

  @Override
  public void open(final Controller controller) {
    this.controller = controller;
    publisher =
        new KafkaRecordPublisher(
            config.producer(),
            partitionId,
            config.maxBatchSize(),
            config.maxQueueSize(),
            config.overflowPolicy(),
            config.flushInterval(),
            logger);
    publisher.start();
    schedulePositionUpdate();
  }

  @Override
  public void close() {
    if (positionUpdateTask != null) {
      positionUpdateTask.cancel();
    }

    if (publisher != null) {
      publisher.close();
      final long finalPosition = publisher.getLastFlushedPosition();
      if (finalPosition >= 0) {
        controller.updateLastExportedRecordPosition(finalPosition);
      }
    }
  }

  @Override
  public void export(final Record<?> record) {
    // KafkaRecordFilter (registered in configure()) handles record-type gating upstream.
    // Only the partition-1 scope for definition/identity types needs enforcing here.
    if (recordHandler.isExcludedByPartitionScope(record)) {
      return;
    }

    publisher.publish(recordHandler.toKafkaExportRecord(record));
  }

  /**
   * For testing only — injects a pre-built publisher and wires up the position-update task without
   * starting the publisher's flush thread.
   */
  void openWithPublisher(final Controller controller, final KafkaRecordPublisher testPublisher) {
    this.controller = controller;
    this.publisher = testPublisher;
    schedulePositionUpdate();
  }

  void checkAndUpdatePosition() {
    final long position = publisher.getLastFlushedPosition();
    if (position > lastReportedPosition) {
      controller.updateLastExportedRecordPosition(position);
      lastReportedPosition = position;
    }
    schedulePositionUpdate();
  }

  private void schedulePositionUpdate() {
    positionUpdateTask =
        controller.scheduleCancellableTask(config.flushInterval(), this::checkAndUpdatePosition);
  }
}
