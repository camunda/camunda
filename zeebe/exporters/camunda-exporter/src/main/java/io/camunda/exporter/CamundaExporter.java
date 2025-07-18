/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter;

import static io.camunda.zeebe.protocol.record.ValueType.AUTHORIZATION;
import static io.camunda.zeebe.protocol.record.ValueType.BATCH_OPERATION_CHUNK;
import static io.camunda.zeebe.protocol.record.ValueType.BATCH_OPERATION_CREATION;
import static io.camunda.zeebe.protocol.record.ValueType.BATCH_OPERATION_EXECUTION;
import static io.camunda.zeebe.protocol.record.ValueType.BATCH_OPERATION_LIFECYCLE_MANAGEMENT;
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
import static io.camunda.zeebe.protocol.record.ValueType.PROCESS_INSTANCE_MIGRATION;
import static io.camunda.zeebe.protocol.record.ValueType.PROCESS_INSTANCE_MODIFICATION;
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
import io.camunda.search.schema.SchemaManager;
import io.camunda.search.schema.SearchEngineClient;
import io.camunda.search.schema.config.SchemaManagerConfiguration;
import io.camunda.search.schema.config.SearchEngineConfiguration;
import io.camunda.webapps.schema.descriptors.AbstractIndexDescriptor;
import io.camunda.webapps.schema.descriptors.index.ImportPositionIndex;
import io.camunda.webapps.schema.descriptors.index.TasklistImportPositionIndex;
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
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
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
    metrics = new CamundaExporterMetrics(context.getMeterRegistry(), context.clock());
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
                metadata,
                clientAdapter.objectMapper(),
                provider.getProcessCache())
            .build();
    LOG.debug("Exporter configured with {}", configuration);
  }

  @Override
  public void open(final Controller controller) {
    this.controller = controller;
    searchEngineClient = clientAdapter.getSearchEngineClient();
    final var schemaManager = createSchemaManager();

    if (!schemaManager.isSchemaReadyForUse()) {
      throw new IllegalStateException("Schema is not ready for use");
    }

    writer = createBatchWriter();

    checkImportersCompletedAndReschedule();
    controller.readMetadata().ifPresent(metadata::deserialize);
    taskManager.start();

    LOG.info("Exporter opened");
  }

  @Override
  public void close() {

    if (writer != null) {
      try {
        flush();
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

    // Brownfield:
    //
    // Before flushing, Tasklist + Operate importers should have finished importing records of the
    // previous version.
    // Flushing is not possible to prevent data corruption (conflicting updates with Importers).
    //
    // For the importers to finish, the Elasticsearch/OpenSearch exporters need to
    // export all records from the previous version. When Importer see records
    // of a new version they can set as completed. Camunda Exporter skips older records to
    // unblock other exporters, and let Importers do their job.
    //
    // As soon new records start to be exported, they get cached. Importers should be able to
    // complete the importing.

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
      flush();
    }
  }

  @Override
  public void purge() {
    searchEngineClient = clientAdapter.getSearchEngineClient();
    final var schemaManager = createSchemaManager();

    // Indices
    final var emptiedIndices = schemaManager.truncateIndices();

    // Delete archived indices
    schemaManager.deleteArchivedIndices();

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

  private SchemaManager createSchemaManager() {
    final var schemaManagerConfiguration = new SchemaManagerConfiguration();
    schemaManagerConfiguration.setCreateSchema(configuration.isCreateSchema());
    return new SchemaManager(
        searchEngineClient,
        provider.getIndexDescriptors(),
        provider.getIndexTemplateDescriptors(),
        SearchEngineConfiguration.of(
            b ->
                b.connect(configuration.getConnect())
                    .index(configuration.getIndex())
                    .retention(configuration.getHistory().getRetention())
                    .schemaManager(schemaManagerConfiguration)),
        clientAdapter.objectMapper());
  }

  private List<String> prefixedNames(final String... names) {
    final var indexPrefix =
        AbstractIndexDescriptor.formatIndexPrefix(configuration.getConnect().getIndexPrefix());
    return Arrays.stream(names).map(s -> indexPrefix + s).toList();
  }

  private void ensureCachedRecordsLessThanBulkSize(final Record<?> record) {
    final var maxCachedRecords = configuration.getBulk().getSize();

    if (writer.getBatchSize() == configuration.getBulk().getSize()) {
      LOG.info(
"""
Cached maximum batch size [{}] number of records, exporting will block at the current position of [{}] while waiting for the importers to finish
processing records from previous version
""",
          configuration.getBulk().getSize(),
          record.getPosition());
    }

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
    final var builder =
        ExporterBatchWriter.Builder.begin(metrics)
            .withCustomErrorHandlers(provider.getCustomErrorHandlers());
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
    if (writer.getBatchSize() == 0) {
      return;
    }

    try (final var ignored = metrics.measureFlushDuration()) {
      metrics.recordBulkSize(writer.getBatchSize());
      final BatchRequest batchRequest = clientAdapter.createBatchRequest().withMetrics(metrics);
      writer.flush(batchRequest);
      metrics.recordFlushOccurrence(Instant.now());
      metrics.stopFlushLatencyMeasurement();
    } catch (final PersistenceException ex) {
      metrics.recordFailedFlush();
      throw new ExporterException(ex.getMessage(), ex);
    }

    // Update the record counters only after the flush was successful. If the synchronous flush
    // fails then the exporter will be invoked with the same record again.
    updateLastExportedPosition(lastPosition);
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
            PROCESS_INSTANCE_MIGRATION,
            PROCESS_INSTANCE_MODIFICATION,
            ROLE,
            VARIABLE,
            VARIABLE_DOCUMENT,
            PROCESS_MESSAGE_SUBSCRIPTION,
            JOB,
            INCIDENT,
            DECISION_EVALUATION,
            PROCESS,
            FORM,
            USER_TASK,
            BATCH_OPERATION_CREATION,
            BATCH_OPERATION_EXECUTION,
            BATCH_OPERATION_LIFECYCLE_MANAGEMENT,
            BATCH_OPERATION_CHUNK);

    @Override
    public boolean acceptType(final RecordType recordType) {
      return recordType.equals(RecordType.EVENT) || recordType.equals(RecordType.COMMAND_REJECTION);
    }

    @Override
    public boolean acceptValue(final ValueType valueType) {
      return VALUE_TYPES_2_EXPORT.contains(valueType);
    }
  }
}
