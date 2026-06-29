/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.zeebe.exporter;

import io.camunda.eventbridge.client.EventBridgeClient;
import io.camunda.eventbridge.client.EventBridgeClient.BatchPublisher;
import io.camunda.eventbridge.zeebe.connector.ZeebeRecordCodec;
import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.protocol.record.Record;
import java.time.Duration;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Zeebe exporter that publishes every exported record to an Event Bridge topic.
 *
 * <p>Records are serialized with {@link ZeebeRecordCodec} as they are exported (the codec copies
 * the value, so it is safe against the exporter's reused record buffer) and accumulated into a
 * batch. The batch is flushed when it reaches {@code batchSize} or after {@code flushIntervalMs};
 * only once a flush is acknowledged is {@link Controller#updateLastExportedRecordPosition(long)}
 * advanced, so delivery is at-least-once. One exporter instance runs per source partition, so all
 * its records go to the Event Bridge partition matching the source partition id.
 *
 * <p>Configure it like any exporter:
 *
 * <pre>{@code
 * exporters:
 *   eventbridge:
 *     className: io.camunda.eventbridge.zeebe.connector.ZeebeRecordExporter
 *     args:
 *       url: http://localhost:8080
 *       topic: zeebe-records
 *       batchSize: 100
 *       flushIntervalMs: 1000
 * }</pre>
 */
public final class ZeebeRecordExporter implements Exporter {

  private final Function<String, EventBridgeClient> clientFactory;
  private final ZeebeRecordCodec codec = new ZeebeRecordCodec();

  private Logger log = LoggerFactory.getLogger(ZeebeRecordExporter.class);
  private ExporterConfiguration config;
  private Controller controller;
  private EventBridgeClient client;

  private BatchPublisher batch;
  private int batchPartition = -1;
  private int batchCount;
  private long batchLastPosition = -1;

  /** Required no-arg constructor used by the exporter runtime. */
  public ZeebeRecordExporter() {
    this(EventBridgeClient::create);
  }

  /** Test seam: lets a test inject a client instead of opening a real connection. */
  ZeebeRecordExporter(final Function<String, EventBridgeClient> clientFactory) {
    this.clientFactory = clientFactory;
  }

  @Override
  public void configure(final Context context) {
    log = context.getLogger();
    config = context.getConfiguration().instantiate(ExporterConfiguration.class);
  }

  @Override
  public void open(final Controller controller) {
    this.controller = controller;
    client = clientFactory.apply(config.url);
    batch = client.newBatch();
    scheduleFlush();
  }

  @Override
  public void export(final Record<?> record) {
    batch.add(Long.toString(record.getKey()), codec.serialize(record));
    batchPartition = record.getPartitionId();
    batchLastPosition = record.getPosition();
    batchCount++;
    if (batchCount >= config.batchSize) {
      flush();
    }
  }

  @Override
  public void close() {
    try {
      flush();
    } finally {
      if (client != null) {
        client.close();
      }
    }
  }

  private void scheduleFlush() {
    if (controller != null && config.flushIntervalMs > 0) {
      controller.scheduleCancellableTask(
          Duration.ofMillis(config.flushIntervalMs), this::flushTick);
    }
  }

  private void flushTick() {
    try {
      flush();
    } catch (final RuntimeException e) {
      log.warn("Scheduled flush to topic {} failed; will retry", config.topic, e);
    } finally {
      scheduleFlush();
    }
  }

  private void flush() {
    if (batchCount == 0) {
      return;
    }
    final long position = batchLastPosition;
    batch.publishToTopic(config.topic, batchPartition).join();
    controller.updateLastExportedRecordPosition(position);
    batch = client.newBatch();
    batchCount = 0;
    batchLastPosition = -1;
  }

  /** Exporter configuration; field names map to the {@code args} block. */
  public static final class ExporterConfiguration {
    public String url = "http://localhost:8080";
    public String topic = "zeebe-records";
    public int batchSize = 100;
    public long flushIntervalMs = 1000;
  }
}
