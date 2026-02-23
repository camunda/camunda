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
import static io.camunda.zeebe.protocol.record.ValueType.BATCH_OPERATION_INITIALIZATION;
import static io.camunda.zeebe.protocol.record.ValueType.BATCH_OPERATION_LIFECYCLE_MANAGEMENT;
import static io.camunda.zeebe.protocol.record.ValueType.CLUSTER_VARIABLE;
import static io.camunda.zeebe.protocol.record.ValueType.DECISION;
import static io.camunda.zeebe.protocol.record.ValueType.DECISION_EVALUATION;
import static io.camunda.zeebe.protocol.record.ValueType.DECISION_REQUIREMENTS;
import static io.camunda.zeebe.protocol.record.ValueType.FORM;
import static io.camunda.zeebe.protocol.record.ValueType.GLOBAL_LISTENER;
import static io.camunda.zeebe.protocol.record.ValueType.GROUP;
import static io.camunda.zeebe.protocol.record.ValueType.HISTORY_DELETION;
import static io.camunda.zeebe.protocol.record.ValueType.INCIDENT;
import static io.camunda.zeebe.protocol.record.ValueType.JOB;
import static io.camunda.zeebe.protocol.record.ValueType.JOB_METRICS_BATCH;
import static io.camunda.zeebe.protocol.record.ValueType.MAPPING_RULE;
import static io.camunda.zeebe.protocol.record.ValueType.MESSAGE_START_EVENT_SUBSCRIPTION;
import static io.camunda.zeebe.protocol.record.ValueType.PROCESS;
import static io.camunda.zeebe.protocol.record.ValueType.PROCESS_INSTANCE;
import static io.camunda.zeebe.protocol.record.ValueType.PROCESS_INSTANCE_CREATION;
import static io.camunda.zeebe.protocol.record.ValueType.PROCESS_INSTANCE_MIGRATION;
import static io.camunda.zeebe.protocol.record.ValueType.PROCESS_INSTANCE_MODIFICATION;
import static io.camunda.zeebe.protocol.record.ValueType.PROCESS_MESSAGE_SUBSCRIPTION;
import static io.camunda.zeebe.protocol.record.ValueType.RESOURCE;
import static io.camunda.zeebe.protocol.record.ValueType.ROLE;
import static io.camunda.zeebe.protocol.record.ValueType.TENANT;
import static io.camunda.zeebe.protocol.record.ValueType.USAGE_METRIC;
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
import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.ExporterException;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.api.context.Context.RecordFilter;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
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
  private SearchEngineClient searchEngineClient;
  private int partitionId;
  private Context context;

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
    this.context = context;
    configuration = context.getConfiguration().instantiate(ExporterConfiguration.class);
    partitionId = context.getPartitionId();

    LOG.info("Configuring exporter with {}", configuration);
    ConfigValidator.validate(configuration);
    context.setFilter(new CamundaExporterRecordFilter());
    verifySetupOfResources();
  }

  @Override
  public void open(final Controller controller) {
    LOG.info("Opening Exporter on partition {}", partitionId);
    this.controller = controller;
    try {
      setupExporterResources();
      searchEngineClient = clientAdapter.getSearchEngineClient();

      try (final var schemaManager = createSchemaManager()) {
        if (!schemaManager.isSchemaReadyForUse()) {
          throw new ExporterException("Schema is not ready for use");
        }
      }

      writer = createBatchWriter();
      controller.readMetadata().ifPresent(metadata::deserialize);
      taskManager.start();
      scheduleDelayedFlush();

      LOG.info("Exporter opened");
    } catch (final Exception e) {
      searchEngineClient.close();
      close();
      throw e;
    }
  }

  @Override
  public void close() {

    if (writer != null) {
      try {
        flush();
        writer = null;
      } catch (final Exception e) {
        LOG.warn("Failed to flush records before closing exporter.", e);
      }
    }

    if (clientAdapter != null) {
      CloseHelper.close(
          error -> LOG.warn("Failed to close elasticsearch client", error), clientAdapter);
      clientAdapter = null;
    }

    if (metrics != null) {
      CloseHelper.close(
          error -> LOG.warn("Failed to remove exporter metrics from registry.", error), metrics);
      metrics = null;
    }

    provider.reset();

    if (taskManager != null) {
      CloseHelper.close(error -> LOG.warn("Failed to close background tasks", error), taskManager);
      taskManager = null;
    }
    LOG.info("Exporter resources closed");
  }

  @Override
  public void export(final Record<?> record) {
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
    try {
      setupExporterResources();
      searchEngineClient = clientAdapter.getSearchEngineClient();
      final List<String> emptiedIndices;
      try (final var schemaManager = createSchemaManager()) {

        // Indices
        emptiedIndices = schemaManager.truncateIndices();

        // Delete archived indices
        schemaManager.deleteArchivedIndices();
      }

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
    } finally {
      close();
    }
  }

  private void verifySetupOfResources() {
    // given the context and configuration
    try {
      // we need setup all resources
      // to ensure that we can create the clients,
      // connect and make use of the configuration given
      setupExporterResources();
    } finally {
      // afterward we need to clean up all the resources
      // as we only wanted to verify the setup and the exporter
      // might simply just run in passive mode - so there is no
      // need to keep the clients opens
      close();
    }
  }

  private void setupExporterResources() {
    metrics = new CamundaExporterMetrics(context.getMeterRegistry(), context.clock());
    clientAdapter = ClientAdapter.of(configuration.getConnect());
    if (metadata == null) {
      metadata = new ExporterMetadata(clientAdapter.objectMapper());
    }
    provider.init(
        configuration,
        clientAdapter.getExporterEntityCacheProvider(),
        context,
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
                provider.getProcessCache(),
                context.clock())
            .build();
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

  private boolean shouldFlush() {
    return writer.getBatchSize() >= configuration.getBulk().getSize()
        || writer.getBatchMemoryEstimateInMb() >= configuration.getBulk().getMemoryLimit();
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

  record CamundaExporterRecordFilter() implements RecordFilter {
    private static final Set<ValueType> VALUE_TYPES_2_EXPORT =
        Set.of(
            USER,
            GROUP,
            MAPPING_RULE,
            AUTHORIZATION,
            TENANT,
            DECISION,
            DECISION_REQUIREMENTS,
            PROCESS_INSTANCE,
            PROCESS_INSTANCE_CREATION,
            PROCESS_INSTANCE_MIGRATION,
            PROCESS_INSTANCE_MODIFICATION,
            ROLE,
            VARIABLE,
            VARIABLE_DOCUMENT,
            PROCESS_MESSAGE_SUBSCRIPTION,
            MESSAGE_START_EVENT_SUBSCRIPTION,
            JOB,
            INCIDENT,
            DECISION_EVALUATION,
            PROCESS,
            FORM,
            RESOURCE,
            USER_TASK,
            BATCH_OPERATION_CREATION,
            BATCH_OPERATION_INITIALIZATION,
            BATCH_OPERATION_EXECUTION,
            BATCH_OPERATION_LIFECYCLE_MANAGEMENT,
            BATCH_OPERATION_CHUNK,
            USAGE_METRIC,
            CLUSTER_VARIABLE,
            HISTORY_DELETION,
            JOB_METRICS_BATCH,
            GLOBAL_LISTENER);

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
