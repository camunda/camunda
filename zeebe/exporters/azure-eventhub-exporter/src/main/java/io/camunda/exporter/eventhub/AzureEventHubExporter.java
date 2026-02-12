/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.eventhub;

import com.azure.messaging.eventhubs.EventData;
import com.azure.messaging.eventhubs.EventDataBatch;
import com.azure.messaging.eventhubs.EventHubProducerClient;
import com.azure.messaging.eventhubs.EventHubClientBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.protocol.record.Record;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Exporter for pushing events to Azure Event Hub. */
public class AzureEventHubExporter implements Exporter {

  private final Logger log = LoggerFactory.getLogger(getClass().getPackageName());
  private final ObjectMapper objectMapper;
  private final List<Record<?>> recordBuffer;

  private AzureEventHubExporterConfiguration configuration;
  private Controller controller;
  private EventHubProducerClient producerClient;
  private long lastExportedPosition = -1;

  public AzureEventHubExporter() {
    this.objectMapper = new ObjectMapper();
    this.objectMapper.registerModule(new JavaTimeModule());
    this.recordBuffer = new ArrayList<>();
  }

  @Override
  public void configure(final Context context) {
    configuration =
        context.getConfiguration().instantiate(AzureEventHubExporterConfiguration.class);
    configuration.validate();
    log.info(
        "Azure Event Hub exporter configured for Event Hub: {}", configuration.getEventHubName());
  }

  @Override
  public void open(final Controller controller) {
    this.controller = controller;

    try {
      producerClient =
          new EventHubClientBuilder()
              .connectionString(configuration.getConnectionString(), configuration.getEventHubName())
              .buildProducerClient();

      scheduleDelayedFlush();
      log.info("Azure Event Hub exporter opened successfully");
    } catch (final Exception ex) {
      closeProducerClient();
      throw new RuntimeException("Failed to open Azure Event Hub exporter", ex);
    }
  }

  @Override
  public void close() {
    try {
      flush();
    } catch (final Exception e) {
      log.warn("Failed to flush records before closing exporter", e);
    }

    closeProducerClient();
    log.info("Azure Event Hub exporter closed");
  }

  @Override
  public void export(final Record<?> record) {
    recordBuffer.add(record);

    if (recordBuffer.size() >= configuration.getMaxBatchSize()) {
      flush();
    }
  }

  private void flush() {
    if (recordBuffer.isEmpty()) {
      return;
    }

    try {
      final EventDataBatch eventBatch = producerClient.createBatch();
      final List<Record<?>> recordsToExport = new ArrayList<>(recordBuffer);
      recordBuffer.clear();

      for (final Record<?> record : recordsToExport) {
        final String recordJson = record.toJson();
        final EventData eventData = new EventData(recordJson);

        if (!eventBatch.tryAdd(eventData)) {
          // If the batch is full, send it and create a new batch
          if (eventBatch.getCount() > 0) {
            producerClient.send(eventBatch);
            log.debug("Sent batch of {} events to Azure Event Hub", eventBatch.getCount());
          }

          // Create a new batch and add the current event
          final EventDataBatch newBatch = producerClient.createBatch();
          if (!newBatch.tryAdd(eventData)) {
            log.warn(
                "Event at position {} is too large to fit in a batch, skipping",
                record.getPosition());
          } else {
            producerClient.send(newBatch);
            log.debug("Sent single event to Azure Event Hub");
          }
        }

        lastExportedPosition = record.getPosition();
      }

      // Send any remaining events in the batch
      if (eventBatch.getCount() > 0) {
        producerClient.send(eventBatch);
        log.debug("Sent final batch of {} events to Azure Event Hub", eventBatch.getCount());
      }

      controller.updateLastExportedRecordPosition(lastExportedPosition);
    } catch (final Exception e) {
      log.error("Failed to export records to Azure Event Hub", e);
      // Re-add records to buffer for retry
      recordBuffer.addAll(0, recordsToExport);
      throw new RuntimeException("Failed to export records to Azure Event Hub", e);
    }
  }

  private void scheduleDelayedFlush() {
    controller.scheduleCancellableTask(
        Duration.ofMillis(configuration.getBatchIntervalMs()), this::attemptFlushAndReschedule);
  }

  private void attemptFlushAndReschedule() {
    try {
      log.debug("Attempting to flush exporter from background task");
      flush();
    } catch (final Throwable e) {
      log.warn("Error during flush. Will retry with next attempt.", e);
    } finally {
      scheduleDelayedFlush();
    }
  }

  private void closeProducerClient() {
    if (producerClient != null) {
      try {
        producerClient.close();
        producerClient = null;
      } catch (final Exception e) {
        log.warn("Failed to close Azure Event Hub producer client", e);
      }
    }
  }
}
