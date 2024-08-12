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
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.operate.exporter.schema.IndexDescriptor;
import io.camunda.zeebe.operate.exporter.schema.IndexMapping.IndexMappingProperty;
import io.camunda.zeebe.operate.exporter.schema.IndexSchemaValidator;
import io.camunda.zeebe.operate.exporter.schema.SchemaManager;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
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
  //  private ElasticsearchScriptBuilder scriptBuilder;
  //  private XMLUtil xmlUtil;

  private final long lastPosition = -1;
  private int batchSize;

  @Override
  public void configure(final Context context) {
    log = context.getLogger();
    //    configuration =
    //
    // context.getConfiguration().instantiate(OperateElasticsearchExporterConfiguration.class);
    //    batchSize =
    //        configuration.bulk.size; // TODO this suplicated configuration.elasticsearch.batchSize
    //    log.debug("Exporter configured with {}", configuration);
    //
    //    validate(configuration);

    context.setFilter(new ElasticsearchRecordFilter());
  }

  @Override
  public void open(final Controller controller) {
    this.controller = controller;
    //    scriptBuilder = new ElasticsearchScriptBuilder();
    //    xmlUtil = new XMLUtil();
    //
    //    client = createClient();
    //
    //    restHighLevelClient = createRestHighLevelClient();
    //
    //    createSchemaWhenNeeded();
    //
    //    writer = createBatchWriter();
    //
    //    scheduleDelayedFlush();
    log.info("Exporter opened");
  }

  @Override
  public void close() {

    log.info("Exporter closed");
  }

  @Override
  public void export(final Record<?> record) {}

  private void createSchemaWhenNeeded(
      final SchemaManager schemaManager, final IndexSchemaValidator schemaValidator) {

    try {
      log.info("SchemaStartup started.");
      log.info("SchemaStartup: validate Operate index mappings.");
      final Map<IndexDescriptor, Set<IndexMappingProperty>> newFields =
          schemaValidator.validateIndexMappings();
      if (configuration.elasticSearch().isCreateSchema() && !schemaValidator.schemaExists()) {
        log.info(
            "SchemaStartup: Operate schema is empty or not complete. Indices will be created.");
        schemaManager.createSchema();
        log.info("SchemaStartup: update Operate index mappings.");
      } else {
        log.info(
            "SchemaStartup: schema won't be created, it either already exist, or schema creation is disabled in configuration.");
      }
      if (configuration.elasticSearch().isCreateSchema()) {
        schemaManager.checkAndUpdateIndices();
      }
      if (!newFields.isEmpty()) {
        if (configuration.elasticSearch().isCreateSchema()) {
          schemaManager.updateSchema(newFields);
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

  private void updateLastExportedPosition() {
    controller.updateLastExportedRecordPosition(lastPosition, null);
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
