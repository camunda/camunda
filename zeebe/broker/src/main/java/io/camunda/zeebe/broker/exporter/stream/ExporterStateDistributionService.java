/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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

public class ExporterStateDistributionService implements AutoCloseable {

  private static final Logger PERIODIC_LOGGER =
      new ThrottledLogger(Loggers.EXPORTER_LOGGER, Duration.ofSeconds(60));
  private final PartitionMessagingService partitionMessagingService;
  private final String exporterStateTopic;
  private final BiConsumer<String, ExporterStateDistributeMessage.ExporterStateEntry>
      exporterStateConsumer;

  public ExporterStateDistributionService(
      final BiConsumer<String, ExporterStateDistributeMessage.ExporterStateEntry>
          exporterStateConsumer,
      final PartitionMessagingService partitionMessagingService,
      final String exporterStateTopic) {
    this.exporterStateConsumer = exporterStateConsumer;
    this.partitionMessagingService = partitionMessagingService;
    this.exporterStateTopic = exporterStateTopic;
  }

  public void subscribeForExporterState(final Executor executor) {
    partitionMessagingService.subscribe(exporterStateTopic, this::storeExporterState, executor);
  }

  private void storeExporterState(final ByteBuffer byteBuffer) {
    final var readBuffer = new UnsafeBuffer(byteBuffer);
    final var distributeMessage = new ExporterStateDistributeMessage();
    distributeMessage.wrap(readBuffer, 0, readBuffer.capacity());

    final var exporterState = distributeMessage.getExporterState();

    Loggers.EXPORTER_LOGGER.trace("Received new exporter state {}", exporterState);
    PERIODIC_LOGGER.debug("Current exporter state {}", exporterState);

    exporterState.forEach(exporterStateConsumer);
  }

  public void distributeExporterState(final ExporterStateDistributeMessage distributeMessage) {
    partitionMessagingService.broadcast(exporterStateTopic, distributeMessage.toByteBuffer());
  }

  @Override
  public void close() {
    partitionMessagingService.unsubscribe(exporterStateTopic);
  }
}
