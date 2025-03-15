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
import static io.camunda.zeebe.protocol.record.ValueType.GROUP;
import static io.camunda.zeebe.protocol.record.ValueType.INCIDENT;
import static io.camunda.zeebe.protocol.record.ValueType.JOB;
import static io.camunda.zeebe.protocol.record.ValueType.MAPPING;
import static io.camunda.zeebe.protocol.record.ValueType.PROCESS;
import static io.camunda.zeebe.protocol.record.ValueType.PROCESS_INSTANCE;
import static io.camunda.zeebe.protocol.record.ValueType.PROCESS_MESSAGE_SUBSCRIPTION;
import static io.camunda.zeebe.protocol.record.ValueType.ROLE;
import static io.camunda.zeebe.protocol.record.ValueType.TENANT;
import static io.camunda.zeebe.protocol.record.ValueType.USER;
import static io.camunda.zeebe.protocol.record.ValueType.USER_TASK;
import static io.camunda.zeebe.protocol.record.ValueType.VARIABLE;
import static io.camunda.zeebe.protocol.record.ValueType.VARIABLE_DOCUMENT;

import io.camunda.exporter.adapters.ClientAdapter;
import io.camunda.exporter.config.ConfigValidator;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.exporter.store.ExporterBatchWriter;
import io.camunda.exporter.tasks.BackgroundTaskManager;
import io.camunda.exporter.tasks.BackgroundTaskManagerFactory;
import io.camunda.search.schema.MappingSource;
import io.camunda.search.schema.SearchEngineClient;
import io.camunda.webapps.schema.descriptors.AbstractIndexDescriptor;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.operate.index.ImportPositionIndex;
import io.camunda.webapps.schema.descriptors.tasklist.index.TasklistImportPositionIndex;
import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.ExporterException;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.api.context.Context.RecordFilter;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.util.SemanticVersion;
import io.camunda.zeebe.util.VisibleForTesting;
import java.time.Duration;
import java.util.*;
import org.agrona.CloseHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CamundaExporter implements Exporter {
  private static final Logger LOG = LoggerFactory.getLogger(CamundaExporter.class);
  private final ExporterResourceProvider provider;
  private Controller controller;
  private ExporterConfiguration configuration;
  private ClientAdapter clientAdapter;
  private ExporterBatchWriter writer;
  private long lastPosition = -1;
  private CamundaExporterMetrics metrics;
  private BackgroundTaskManager taskManager;
  private ExporterMetadata metadata;
  private boolean exporterCanFlush = false;
  private boolean zeebeIndicesVersion87Exist = false;
  private SearchEngineClient searchEngineClient;
  private int partitionId;

  public CamundaExporter() {
    // the metadata will be initialized on open
    this(new DefaultExporterResourceProvider(), null);
  }

  @VisibleForTesting
  public CamundaExporter(final ExporterResourceProvider provider) {
    this(provider, null);
  }

  @VisibleForTesting
  public CamundaExporter(final ExporterResourceProvider provider, final ExporterMetadata metadata) {
    this.provider = provider;
    this.metadata = metadata;
  }

  @Override
  public void configure(final Context context) {
    configuration = context.getConfiguration().instantiate(ExporterConfiguration.class);
    ConfigValidator.validate(configuration);
    context.setFilter(new CamundaExporterRecordFilter());
    metrics = new CamundaExporterMetrics(context.getMeterRegistry());
    clientAdapter = ClientAdapter.of(configuration.getConnect());
    if (metadata == null) {
      metadata = new ExporterMetadata(clientAdapter.objectMapper());
    }
    partitionId = context.getPartitionId();
    provider.init(
        configuration,
        clientAdapter.getExporterEntityCacheProvider(),
        context.getMeterRegistry(),
        metadata,
        clientAdapter.objectMapper());

    taskManager =
        new BackgroundTaskManagerFactory(
                context.getPartitionId(),
                context.getConfiguration().getId().toLowerCase(),
                configuration,
                provider,
                metrics,
                context.getLogger(),
                metadata)
            .build();
    LOG.debug("Exporter configured with {}", configuration);
  }

  @Override
  public void open(final Controller controller) {
    this.controller = controller;
    searchEngineClient = clientAdapter.getSearchEngineClient();

    writer = createBatchWriter();

    checkImportersCompletedAndReschedule();
    controller.readMetadata().ifPresent(metadata::deserialize);
    taskManager.start();

    LOG.info("Exporter opened");
  }

  @Override
  public void close() {
    provider.close();

    if (writer != null) {
      try {
        flush();
        updateLastExportedPosition(lastPosition);
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

    final var recordVersion = getVersion(record.getBrokerVersion());

    if (recordVersion.major() == 8 && recordVersion.minor() < 8) {
      LOG.debug(
          "Skip record with broker version '{}'. Last exported position will be updated to '{}'",
          record.getBrokerVersion(),
          record.getPosition());
      updateLastExportedPosition(record.getPosition());
      return;
    }

    if (configuration.getIndex().shouldWaitForImporters() && !exporterCanFlush) {
      ensureCachedRecordsLessThanBulkSize(record);

      writer.addRecord(record);

      LOG.info(
          "Waiting for importers to finish, cached record with key {} but did not flush",
          record.getKey());
      return;
    }

    if (writer.getBatchSize() == 0) {
      metrics.startFlushLatencyMeasurement();
    }

    // adding record is idempotent
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
      updateLastExportedPosition(lastPosition);
    }
  }

  @Override
  public void purge() {
    searchEngineClient = clientAdapter.getSearchEngineClient();
    // Indices
    final var emptiedIndices = truncateIndices();

    // Delete archived indices
    deleteArchivedIndices();

    // At this point, several indices still have data, e.g.
    // deployment, tasklist-task, process, operate-event, operate-list-view,
    // operate-flownode-instance, process-instance-creation, user-task,
    // process-instance
    // If I stop deleting things right here, tests will not pass.

    // Indices, not managed by the SchemaManager
    // This code can be removed, once we have the unified SchemaManager, which will take care of
    // deleting all indices it manages (#26890).
    final var indexNames = String.join(",", prefixedNames("operate-*", "tasklist-*"));
    LOG.debug("Purging exporter indexes: {}", indexNames);

    searchEngineClient.getMappings(indexNames, MappingSource.INDEX).keySet().stream()
        .filter(index -> !emptiedIndices.contains(index))
        .forEach(searchEngineClient::truncateIndex);
  }

  private List<String> truncateIndices() {
    final var indices =
        provider.getIndexDescriptors().stream().map(IndexDescriptor::getFullQualifiedName).toList();
    indices.forEach(searchEngineClient::truncateIndex);
    return indices;
  }

  private void deleteArchivedIndices() {
    final var liveIndices =
        provider.getIndexDescriptors().stream().map(IndexDescriptor::getFullQualifiedName).toList();
    final var archivedIndices =
        liveIndices.stream()
            .map(indexName -> indexName + "*")
            .map(idxWildcard -> searchEngineClient.getMappings(idxWildcard, MappingSource.INDEX))
            .map(Map::keySet)
            .flatMap(Collection::stream)
            .filter(index -> !liveIndices.contains(index))
            .toList();
    archivedIndices.forEach(searchEngineClient::deleteIndex);
    LOG.debug("Deleted archived indices '{}'", archivedIndices);
  }

  @VisibleForTesting
  ExporterMetadata getMetadata() {
    return metadata;
  }

  private List<String> prefixedNames(final String... names) {
    final var indexPrefix =
        AbstractIndexDescriptor.formatIndexPrefix(configuration.getIndex().getPrefix());
    return Arrays.stream(names).map(s -> indexPrefix + s).toList();
  }

  private void ensureCachedRecordsLessThanBulkSize(final Record<?> record) {
    final var maxCachedRecords = configuration.getBulk().getSize();

    if (writer.getBatchSize() >= maxCachedRecords) {
      final var warnMsg =
          String.format(
              "Reached the max bulk size amount of cached records [%d] while waiting for importers to finish, retrying export for record at position [%s]",
              maxCachedRecords, record.getPosition());
      LOG.warn(warnMsg);
      throw new IllegalStateException(warnMsg);
    }
  }

  private SemanticVersion getVersion(final String version) {
    return SemanticVersion.parse(version)
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Unsupported record broker version: ["
                        + version
                        + "] Must be a semantic version."));
  }

  private boolean shouldFlush() {
    return writer.getBatchSize() >= configuration.getBulk().getSize();
  }

  private ExporterBatchWriter createBatchWriter() {
    final var builder = ExporterBatchWriter.Builder.begin();
    provider.getExportHandlers().forEach(builder::withHandler);
    builder.withCustomErrorHandlers(provider.getCustomErrorHandlers());
    return builder.build();
  }

  private void scheduleDelayedFlush() {
    controller.scheduleCancellableTask(
        Duration.ofSeconds(configuration.getBulk().getDelay()), this::flushAndReschedule);
  }

  private void flushAndReschedule() {
    try {
      flush();
      updateLastExportedPosition(lastPosition);
    } catch (final Exception e) {
      LOG.warn("Unexpected exception occurred on periodically flushing bulk, will retry later.", e);
    }
    scheduleDelayedFlush();
  }

  private void scheduleImportersCompletedCheck() {
    controller.scheduleCancellableTask(
        Duration.ofSeconds(10), this::checkImportersCompletedAndReschedule);
  }

  private void checkImportersCompletedAndReschedule() {
    if (!configuration.getIndex().shouldWaitForImporters()) {
      LOG.debug(
          "Waiting for importers to complete is disabled, thus scheduling delayed flush regardless of importer state.");
      scheduleDelayedFlush();
      return;
    }
    if (!exporterCanFlush) {
      scheduleImportersCompletedCheck();
    }
    try {
      final var importPositionIndices =
          provider.getIndexDescriptors().stream()
              .filter(
                  d -> d instanceof ImportPositionIndex || d instanceof TasklistImportPositionIndex)
              .toList();

      if (!zeebeIndicesVersion87Exist) {
        zeebeIndicesVersion87Exist =
            !searchEngineClient
                .getMappings(
                    configuration.getIndex().getZeebeIndexPrefix() + "*8.7.*_", MappingSource.INDEX)
                .isEmpty();
      }

      exporterCanFlush =
          !zeebeIndicesVersion87Exist
              || searchEngineClient.importersCompleted(partitionId, importPositionIndices);
    } catch (final Exception e) {
      LOG.warn("Unexpected exception occurred checking importers completed, will retry later.", e);
    }

    if (exporterCanFlush) {
      scheduleDelayedFlush();
    }
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

  private void updateLastExportedPosition(final long lastPosition) {
    final var serialized = metadata.serialize();
    controller.updateLastExportedRecordPosition(lastPosition, serialized);
  }

  private record CamundaExporterRecordFilter() implements RecordFilter {
    private static final Set<ValueType> VALUE_TYPES_2_EXPORT =
        Set.of(
            USER,
            GROUP,
            MAPPING,
            AUTHORIZATION,
            TENANT,
            DECISION,
            DECISION_REQUIREMENTS,
            PROCESS_INSTANCE,
            ROLE,
            VARIABLE,
            VARIABLE_DOCUMENT,
            PROCESS_MESSAGE_SUBSCRIPTION,
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
