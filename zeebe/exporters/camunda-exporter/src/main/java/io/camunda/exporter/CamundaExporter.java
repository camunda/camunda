/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter;

import static io.camunda.zeebe.protocol.record.ValueType.AUTHORIZATION;
import static io.camunda.zeebe.protocol.record.ValueType.USER;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.util.VisibleForTesting;
import io.camunda.exporter.clients.elasticsearch.ElasticsearchClientFactory;
import io.camunda.exporter.config.ElasticsearchExporterConfiguration;
import io.camunda.exporter.exceptions.ElasticsearchExporterException;
import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.handlers.AuthorizationRecordValueExportHandler;
import io.camunda.exporter.handlers.UserRecordValueExportHandler;
import io.camunda.exporter.schema.ElasticsearchEngineClient;
import io.camunda.exporter.schema.ElasticsearchEngineClient.MappingSource;
import io.camunda.exporter.schema.ElasticsearchSchemaManager;
import io.camunda.exporter.schema.IndexMappingProperty;
import io.camunda.exporter.schema.IndexSchemaValidator;
import io.camunda.exporter.schema.SchemaManager;
import io.camunda.exporter.schema.SearchEngineClient;
import io.camunda.exporter.store.ElasticsearchBatchRequest;
import io.camunda.exporter.store.ExporterBatchWriter;
import io.camunda.exporter.utils.ElasticsearchScriptBuilder;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.api.context.Context.RecordFilter;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CamundaExporter implements Exporter {
  private static final Logger LOG = LoggerFactory.getLogger(CamundaExporter.class);

  private Controller controller;
  private ElasticsearchExporterConfiguration configuration;
  private ElasticsearchClient client;
  private ExporterBatchWriter writer;
  private long lastPosition = -1;
  private final ExporterResourceProvider provider;

  public CamundaExporter() {
    this(new DefaultExporterResourceProvider());
  }

  @VisibleForTesting
  public CamundaExporter(final ExporterResourceProvider provider) {
    this.provider = provider;
  }

  @Override
  public void configure(final Context context) {
    configuration =
        context.getConfiguration().instantiate(ElasticsearchExporterConfiguration.class);
    // TODO validate configuration
    context.setFilter(new ElasticsearchRecordFilter());
    LOG.debug("Exporter configured with {}", configuration);
  }

  @Override
  public void open(final Controller controller) {
    this.controller = controller;
    client = createClient();
    final var searchEngineClient = new ElasticsearchEngineClient(client);
    final var schemaManager = createSchemaManager(searchEngineClient);
    final var schemaValidator = new IndexSchemaValidator(schemaManager);

    schemaStartup(schemaManager, schemaValidator, searchEngineClient);
    writer = createBatchWriter();

    scheduleDelayedFlush();

    LOG.info("Exporter opened");
  }

  @Override
  public void close() {
    try {
      flush();
      updateLastExportedPosition();
    } catch (final Exception e) {
      LOG.warn("Failed to flush records before closing exporter.", e);
    }

    try {
      client._transport().close();
    } catch (final Exception e) {
      LOG.warn("Failed to close elasticsearch client", e);
    }

    LOG.info("Exporter closed");
  }

  @Override
  public void export(final Record<?> record) {
    writer.addRecord(record);
    lastPosition = record.getPosition();
    if (shouldFlush()) {
      flush();
      // Update the record counters only after the flush was successful. If the synchronous flush
      // fails then the exporter will be invoked with the same record again.
      updateLastExportedPosition();
    }
  }

  private void schemaStartup(
      final SchemaManager schemaManager,
      final IndexSchemaValidator schemaValidator,
      final SearchEngineClient searchEngineClient) {
    if (!configuration.elasticsearch.isCreateSchema()) {
      LOG.info(
          "Will not make any changes to indices and index templates as [createSchema] is false");
      return;
    }

    if (configuration.elasticsearch.isIlmEnabled()) {
      searchEngineClient.putIndexLifeCyclePolicy(
          configuration.elasticsearch.getIlmPolicyName(),
          configuration.elasticsearch.getIlmMinDeletionAge());
    }

    final var newIndexProperties = validateIndices(schemaValidator, searchEngineClient);
    final var newIndexTemplateProperties =
        validateIndexTemplates(schemaValidator, searchEngineClient);
    //  used to create any indices/templates which don't exist
    schemaManager.initialiseResources();

    //  used to update existing indices/templates
    schemaManager.updateSchema(newIndexProperties);
    schemaManager.updateSchema(newIndexTemplateProperties);
  }

  private Map<IndexDescriptor, Set<IndexMappingProperty>> validateIndices(
      final IndexSchemaValidator schemaValidator, final SearchEngineClient searchEngineClient) {
    final var currentIndices =
        searchEngineClient.getMappings(
            configuration.elasticsearch.getIndexPrefix() + "*", MappingSource.INDEX);

    return schemaValidator.validateIndexMappings(currentIndices, provider.getIndexDescriptors());
  }

  private Map<IndexDescriptor, Set<IndexMappingProperty>> validateIndexTemplates(
      final IndexSchemaValidator schemaValidator, final SearchEngineClient searchEngineClient) {
    final var currentTemplates =
        searchEngineClient.getMappings(
            configuration.elasticsearch.getIndexPrefix() + "*", MappingSource.INDEX_TEMPLATE);

    return schemaValidator.validateIndexMappings(
        currentTemplates,
        provider.getIndexTemplateDescriptors().stream()
            .map(IndexDescriptor.class::cast)
            .collect(Collectors.toSet()));
  }

  private SchemaManager createSchemaManager(final SearchEngineClient searchEngineClient) {
    return new ElasticsearchSchemaManager(
        searchEngineClient,
        provider.getIndexDescriptors(),
        provider.getIndexTemplateDescriptors(),
        configuration.elasticsearch);
  }

  private ElasticsearchClient createClient() {
    return ElasticsearchClientFactory.INSTANCE.create(configuration.elasticsearch);
  }

  private boolean shouldFlush() {
    // FIXME should compare against both batch size and memory limit
    return writer.getBatchSize() >= configuration.bulk.getSize();
  }

  private ExporterBatchWriter createBatchWriter() {
    // TODO register all handlers here
    return ExporterBatchWriter.Builder.begin()
        .withHandler(new UserRecordValueExportHandler())
        .withHandler(new AuthorizationRecordValueExportHandler())
        .build();
  }

  private void scheduleDelayedFlush() {
    controller.scheduleCancellableTask(
        Duration.ofSeconds(configuration.bulk.getDelay()), this::flushAndReschedule);
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
      // TODO revisit the need to pass the BulkRequestBuilder and the ElasticsearchScriptBuilder as
      // params here
      final ElasticsearchBatchRequest batchRequest =
          new ElasticsearchBatchRequest(
              client, new BulkRequest.Builder(), new ElasticsearchScriptBuilder());
      writer.flush(batchRequest);
    } catch (final PersistenceException ex) {
      throw new ElasticsearchExporterException(ex.getMessage(), ex);
    }
  }

  private void updateLastExportedPosition() {
    controller.updateLastExportedRecordPosition(lastPosition);
  }

  private record ElasticsearchRecordFilter() implements RecordFilter {
    // TODO include other value types to export
    private static final Set<ValueType> VALUE_TYPES_2_EXPORT = Set.of(USER, AUTHORIZATION);

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
