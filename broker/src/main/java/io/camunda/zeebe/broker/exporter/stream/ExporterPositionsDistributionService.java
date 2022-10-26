/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.exporter.stream;

import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.system.partitions.PartitionMessagingService;
import io.camunda.zeebe.util.logging.ThrottledLogger;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;

public class ExporterPositionsDistributionService implements AutoCloseable {

  private static final Logger PERIODIC_LOGGER =
      new ThrottledLogger(Loggers.EXPORTER_LOGGER, Duration.ofSeconds(60));
  private final PartitionMessagingService partitionMessagingService;
  private final String exporterPositionsTopic;
  private final BiConsumer<String, Long> exporterPositionConsumer;

  public ExporterPositionsDistributionService(
      final BiConsumer<String, Long> exporterPositionConsumer,
      final PartitionMessagingService partitionMessagingService,
      final String exporterTopic) {
    this.exporterPositionConsumer = exporterPositionConsumer;
    this.partitionMessagingService = partitionMessagingService;
    exporterPositionsTopic = exporterTopic;
  }

  public void subscribeForExporterPositions(final Executor executor) {
    partitionMessagingService.subscribe(
        exporterPositionsTopic, this::storeExporterPositions, executor);
  }

  private void storeExporterPositions(final ByteBuffer byteBuffer) {
    final var readBuffer = new UnsafeBuffer(byteBuffer);
    final var exportPositionsMessage = new ExporterPositionsMessage();
    exportPositionsMessage.wrap(readBuffer, 0, readBuffer.capacity());

    final var exporterPositions = exportPositionsMessage.getExporterPositions();

    Loggers.EXPORTER_LOGGER.trace("Received new exporter state {}", exporterPositions);
    PERIODIC_LOGGER.debug("Current exporter state {}", exporterPositions);

    exporterPositions.forEach(exporterPositionConsumer);
  }

  public void distributeExporterPositions(final ExporterPositionsMessage exporterPositionsMessage) {
    partitionMessagingService.broadcast(
        exporterPositionsTopic, exporterPositionsMessage.toByteBuffer());
  }

  @Override
  public void close() {
    partitionMessagingService.unsubscribe(exporterPositionsTopic);
  }
}
