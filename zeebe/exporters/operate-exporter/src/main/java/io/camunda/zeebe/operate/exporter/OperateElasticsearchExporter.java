/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.operate.exporter;

import static io.camunda.zeebe.protocol.record.ValueType.PROCESS;
import static io.camunda.zeebe.protocol.record.ValueType.PROCESS_INSTANCE;
import static io.camunda.zeebe.protocol.record.ValueType.VARIABLE;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.DatabaseInfoProvider;
import io.camunda.operate.conditions.DatabaseType;
import io.camunda.operate.connect.ElasticsearchConnectorHelper;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.property.DatastoreProperties;
import io.camunda.operate.schema.IndexMapping.IndexMappingProperty;
import io.camunda.operate.schema.factories.DatastoreClientHolder;
import io.camunda.operate.schema.factories.IndexDescriptorHolder;
import io.camunda.operate.schema.factories.SchemaManagerHolder;
import io.camunda.operate.schema.indices.IndexDescriptor;
import io.camunda.operate.store.elasticsearch.NewElasticsearchBatchRequest;
import io.camunda.operate.util.ElasticsearchScriptBuilder;
import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.operate.exporter.handlers.FlowNodeInstanceProcessInstanceHandler;
import io.camunda.zeebe.operate.exporter.handlers.ProcessHandler;
import io.camunda.zeebe.operate.exporter.handlers.SequenceFlowHandler;
import io.camunda.zeebe.operate.exporter.handlers.VariableHandler;
import io.camunda.zeebe.operate.exporter.util.XMLUtil;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.elasticsearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OperateElasticsearchExporter implements Exporter {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private Logger log = LoggerFactory.getLogger(getClass().getPackageName());
  private Controller controller;
  private OperateElasticsearchExporterConfiguration configuration;
  private RestHighLevelClient restHighLevelClient;
  private ElasticsearchClient client;
  private ExportBatchWriter writer;
  private ElasticsearchScriptBuilder scriptBuilder;
  private XMLUtil xmlUtil;

  private long lastPosition = -1;
  private int batchSize;

  @Override
  public void configure(final Context context) {
    log = context.getLogger();
    configuration =
        context.getConfiguration().instantiate(OperateElasticsearchExporterConfiguration.class);
    batchSize =
        configuration.bulk.size; // TODO this suplicated configuration.elasticsearch.batchSize
    log.debug("Exporter configured with {}", configuration);

    validate(configuration);

    context.setFilter(new ElasticsearchRecordFilter());
  }

  @Override
  public void open(final Controller controller) {
    this.controller = controller;
    scriptBuilder = new ElasticsearchScriptBuilder();
    xmlUtil = new XMLUtil();

    client = createClient();

    restHighLevelClient = createRestHighLevelClient();

    createSchemaWhenNeeded();

    writer = createBatchWriter();

    scheduleDelayedFlush();
    log.info("Exporter opened");
  }

  @Override
  public void close() {

    try {
      flush();
      updateLastExportedPosition();
    } catch (final Exception e) {
      log.warn("Failed to flush records before closing exporter.", e);
    }

    try {
      client._transport().close();
    } catch (final Exception e) {
      log.warn("Failed to close elasticsearch client", e);
    }

    log.info("Exporter closed");
  }

  @Override
  public void export(final Record<?> record) {
    writer.addRecord(record);
    lastPosition = record.getPosition();
    if (writer.hasAtLeastEntities(batchSize)) {
      flush();
      // Update the record counters only after the flush was successful. If the synchronous flush
      // fails then the exporter will be invoked with the same record again.
      updateLastExportedPosition();
    }
  }

  private void createSchemaWhenNeeded() {
    // TODO Opensearch
    final DatabaseInfoProvider databaseInfoProvider =
        new DatabaseInfoProvider() {
          @Override
          public DatabaseType getCurrent() {
            return DatabaseType.Elasticsearch;
          }

          @Override
          public boolean isElasticsearch() {
            return true;
          }

          @Override
          public boolean isOpensearch() {
            return false;
          }
        };

    final DatastoreProperties datastoreProperties = configuration.elasticsearch;
    DatastoreClientHolder.init(client, restHighLevelClient);
    new SchemaManagerHolder()
        .init(
            databaseInfoProvider, datastoreProperties, DatastoreClientHolder.getInstance(), MAPPER);

    SchemaManagerHolder.getInstance().getSchemaStartup().initializeSchema();

    try {
      log.info("SchemaStartup started.");
      log.info("SchemaStartup: validate Operate index mappings.");
      final SchemaManagerHolder schemaManagerHolder = SchemaManagerHolder.getInstance();
      final Map<IndexDescriptor, Set<IndexMappingProperty>> newFields =
          schemaManagerHolder.getIndexSchemaValidator().validateIndexMappings();
      if (configuration.elasticsearch.isCreateSchema()
          && !schemaManagerHolder.getIndexSchemaValidator().schemaExists()) {
        log.info(
            "SchemaStartup: Operate schema is empty or not complete. Indices will be created.");
        schemaManagerHolder.getSchemaManager().createSchema();
        log.info("SchemaStartup: update Operate index mappings.");
      } else {
        log.info(
            "SchemaStartup: schema won't be created, it either already exist, or schema creation is disabled in configuration.");
      }
      if (configuration.elasticsearch.isCreateSchema()) {
        schemaManagerHolder.getSchemaManager().checkAndUpdateIndices();
      }
      if (!newFields.isEmpty()) {
        if (configuration.elasticsearch.isCreateSchema()) {
          schemaManagerHolder.getSchemaManager().updateSchema(newFields);
        } else {
          log.info(
              "SchemaStartup: schema won't be updated as schema creation is disabled in configuration.");
        }
      }
      log.info("SchemaStartup finished.");
    } catch (final Exception ex) {
      log.error("Schema startup failed: " + ex.getMessage(), ex);
      throw ex;
    }
  }

  private void validate(final OperateElasticsearchExporterConfiguration configuration) {
    // FIXME
  }

  protected ElasticsearchClient createClient() {
    return ElasticsearchConnectorHelper.INSTANCE.createElasticsearchClient(
        configuration.elasticsearch);
  }

  protected RestHighLevelClient createRestHighLevelClient() {
    return ElasticsearchConnectorHelper.INSTANCE.createEsClient(configuration.elasticsearch);
  }

  private void flushAndReschedule() {
    try {
      flush();
      updateLastExportedPosition();
    } catch (final Exception e) {
      log.warn("Unexpected exception occurred on periodically flushing bulk, will retry later.", e);
    }
    scheduleDelayedFlush();
  }

  private void scheduleDelayedFlush() {
    controller.scheduleCancellableTask(
        Duration.ofSeconds(configuration.bulk.delay), this::flushAndReschedule);
  }

  private void flush() {
    try {
      final NewElasticsearchBatchRequest batchRequest =
          new NewElasticsearchBatchRequest(client, new BulkRequest.Builder(), scriptBuilder);
      writer.flush(batchRequest);
    } catch (final PersistenceException ex) {
      throw new ElasticsearchExporterException(ex.getMessage(), ex);
    }
  }

  private void updateLastExportedPosition() {
    controller.updateLastExportedRecordPosition(lastPosition, null);
  }

  private ExportBatchWriter createBatchWriter() {
    final String indexPrefix = configuration.elasticsearch.getIndexPrefix();
    return ExportBatchWriter.Builder.begin()
        // #processProcessRecords
        // processZeebeRecordProcessor.processDeploymentRecord
        .withHandler(
            new ProcessHandler(IndexDescriptorHolder.getInstance().getProcessIndex(), xmlUtil))
        .withHandler(
            new VariableHandler(
                IndexDescriptorHolder.getInstance().getVariableTemplate(),
                configuration.elasticsearch.getVariableSizeThreshold()))
        .withHandler(
            new SequenceFlowHandler(IndexDescriptorHolder.getInstance().getSequenceFlowTemplate()))
        .withHandler(
            new FlowNodeInstanceProcessInstanceHandler(
                IndexDescriptorHolder.getInstance().getFlowNodeInstanceTemplate()))
        .build();
  }

  private static final class ElasticsearchRecordFilter implements Context.RecordFilter {
    private static final List<ValueType> VALUE_TYPES_2_IMPORT =
        List.of(
            PROCESS, VARIABLE, PROCESS_INSTANCE
            //            DECISION,
            //            DECISION_REQUIREMENTS,
            //            DECISION_EVALUATION,
            //            JOB,
            //            INCIDENT,
            //            VARIABLE_DOCUMENT,
            //            PROCESS_MESSAGE_SUBSCRIPTION,
            //            USER_TASK
            );

    @Override
    public boolean acceptType(final RecordType recordType) {
      return recordType.equals(RecordType.EVENT);
    }

    @Override
    public boolean acceptValue(final ValueType valueType) {
      return VALUE_TYPES_2_IMPORT.contains(valueType);
    }
  }
}
