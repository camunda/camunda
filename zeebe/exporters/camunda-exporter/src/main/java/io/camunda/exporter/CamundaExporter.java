/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter;

import static io.camunda.zeebe.protocol.record.ValueType.AUTHORIZATION;
import static io.camunda.zeebe.protocol.record.ValueType.DECISION;
import static io.camunda.zeebe.protocol.record.ValueType.DECISION_EVALUATION;
import static io.camunda.zeebe.protocol.record.ValueType.DECISION_REQUIREMENTS;
import static io.camunda.zeebe.protocol.record.ValueType.FORM;
import static io.camunda.zeebe.protocol.record.ValueType.INCIDENT;
import static io.camunda.zeebe.protocol.record.ValueType.JOB;
import static io.camunda.zeebe.protocol.record.ValueType.PROCESS;
import static io.camunda.zeebe.protocol.record.ValueType.PROCESS_INSTANCE;
import static io.camunda.zeebe.protocol.record.ValueType.USER;
import static io.camunda.zeebe.protocol.record.ValueType.USER_TASK;
import static io.camunda.zeebe.protocol.record.ValueType.VARIABLE;

import co.elastic.clients.util.VisibleForTesting;
import io.camunda.exporter.adapters.ClientAdapter;
import io.camunda.exporter.config.ConfigValidator;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.exporter.schema.SchemaManager;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.exporter.store.ExporterBatchWriter;
import io.camunda.exporter.tasks.BackgroundTaskManager;
import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.ExporterException;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.api.context.Context.RecordFilter;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import java.time.Duration;
import java.util.Set;
import org.agrona.CloseHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CamundaExporter implements Exporter {
  private static final Logger LOG = LoggerFactory.getLogger(CamundaExporter.class);

  private Controller controller;
  private ExporterConfiguration configuration;
  private ClientAdapter clientAdapter;
  private ExporterBatchWriter writer;
  private long lastPosition = -1;
  private final ExporterResourceProvider provider;
  private CamundaExporterMetrics metrics;
  private Logger logger;
  private BackgroundTaskManager taskManager;

  public CamundaExporter() {
    this(new DefaultExporterResourceProvider());
  }

  @VisibleForTesting
  public CamundaExporter(final ExporterResourceProvider provider) {
    this.provider = provider;
  }

  @Override
  public void configure(final Context context) {
    logger = context.getLogger();
    configuration = context.getConfiguration().instantiate(ExporterConfiguration.class);
    ConfigValidator.validate(configuration);
    context.setFilter(new CamundaExporterRecordFilter());
    metrics = new CamundaExporterMetrics(context.getMeterRegistry());
    clientAdapter = ClientAdapter.of(configuration);
    provider.init(configuration, clientAdapter.getExporterEntityCacheProvider());

    taskManager =
        BackgroundTaskManager.create(
            context.getPartitionId(),
            context.getConfiguration().getId().toLowerCase(),
            configuration,
            provider,
            metrics,
            logger);
    LOG.debug("Exporter configured with {}", configuration);
  }

  @Override
  public void open(final Controller controller) {
    this.controller = controller;
    final var searchEngineClient = clientAdapter.getSearchEngineClient();
    final var schemaManager =
        new SchemaManager(
            searchEngineClient,
            provider.getIndexDescriptors(),
            provider.getIndexTemplateDescriptors(),
            configuration);

    schemaManager.startup();

    writer = createBatchWriter();

    scheduleDelayedFlush();

    // // start archiver after the schema has been created to avoid transient errors
    if (configuration.getArchiver().isRolloverEnabled()) {
      taskManager.start();
    }

    LOG.info("Exporter opened");
  }

  @Override
  public void close() {
    if (writer != null) {
      try {
        flush();
        updateLastExportedPosition();
      } catch (final Exception e) {
        LOG.warn("Failed to flush records before closing exporter.", e);
      }
    }

    if (clientAdapter != null) {
      try {
        clientAdapter.close();
      } catch (final Exception e) {
        LOG.warn("Failed to close elasticsearch client", e);
      }
    }

    CloseHelper.close(error -> LOG.warn("Failed to close background tasks", error), taskManager);
    LOG.info("Exporter closed");
  }

  @Override
  public void export(final Record<?> record) {
    if (writer.getBatchSize() == 0) {
      metrics.startFlushLatencyMeasurement();
    }

    writer.addRecord(record);
    lastPosition = record.getPosition();

    if (shouldFlush()) {
      try (final var ignored = metrics.measureFlushDuration()) {
        flush();
        metrics.stopFlushLatencyMeasurement();
      } catch (final ExporterException e) {
        metrics.recordFailedFlush();
        throw e;
      }
      // Update the record counters only after the flush was successful. If the synchronous flush
      // fails then the exporter will be invoked with the same record again.
      updateLastExportedPosition();
    }
  }

  private boolean shouldFlush() {
    return writer.getBatchSize() >= configuration.getBulk().getSize();
  }

  private ExporterBatchWriter createBatchWriter() {
    final var builder = ExporterBatchWriter.Builder.begin();
    provider.getExportHandlers().forEach(builder::withHandler);
    return builder.build();
  }

  private void scheduleDelayedFlush() {
    controller.scheduleCancellableTask(
        Duration.ofSeconds(configuration.getBulk().getDelay()), this::flushAndReschedule);
  }

  private void flushAndReschedule() {
    try {
      flush();
      updateLastExportedPosition();
    } catch (final Exception e) {
      LOG.warn("Unexpected exception occurred on periodically flushing bulk, will retry later.", e);
    }
    scheduleDelayedFlush();
  }

  private void flush() {
    try {
      metrics.recordBulkSize(writer.getBatchSize());
      final BatchRequest batchRequest = clientAdapter.createBatchRequest();
      writer.flush(batchRequest);

    } catch (final PersistenceException ex) {
      throw new ExporterException(ex.getMessage(), ex);
    }
  }

  private void updateLastExportedPosition() {
    controller.updateLastExportedRecordPosition(lastPosition);
  }

  private record CamundaExporterRecordFilter() implements RecordFilter {
    private static final Set<ValueType> VALUE_TYPES_2_EXPORT =
        Set.of(
            USER,
            AUTHORIZATION,
            DECISION,
            DECISION_REQUIREMENTS,
            PROCESS_INSTANCE,
            VARIABLE,
            JOB,
            INCIDENT,
            DECISION_EVALUATION,
            PROCESS,
            FORM,
            USER_TASK);

    @Override
    public boolean acceptType(final RecordType recordType) {
      return recordType.equals(RecordType.EVENT);
    }

    @Override
    public boolean acceptValue(final ValueType valueType) {
      return VALUE_TYPES_2_EXPORT.contains(valueType);
    }
  }
}
