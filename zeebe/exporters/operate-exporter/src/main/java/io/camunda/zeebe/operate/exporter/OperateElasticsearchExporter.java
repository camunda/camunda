/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.operate.exporter;

import static io.camunda.zeebe.protocol.record.ValueType.INCIDENT;
import static io.camunda.zeebe.protocol.record.ValueType.PROCESS;
import static io.camunda.zeebe.protocol.record.ValueType.PROCESS_INSTANCE;
import static io.camunda.zeebe.protocol.record.ValueType.USER_TASK;
import static io.camunda.zeebe.protocol.record.ValueType.VARIABLE;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.indices.ProcessIndex;
import io.camunda.operate.schema.templates.FlowNodeInstanceTemplate;
import io.camunda.operate.schema.templates.SequenceFlowTemplate;
import io.camunda.operate.schema.templates.UserTaskTemplate;
import io.camunda.operate.schema.templates.VariableTemplate;
import io.camunda.operate.store.elasticsearch.NewElasticsearchBatchRequest;
import io.camunda.operate.util.ElasticsearchScriptBuilder;
import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.operate.exporter.handlers.FlowNodeInstanceIncidentHandler;
import io.camunda.zeebe.operate.exporter.handlers.FlowNodeInstanceProcessInstanceHandler;
import io.camunda.zeebe.operate.exporter.handlers.ProcessHandler;
import io.camunda.zeebe.operate.exporter.handlers.SequenceFlowHandler;
import io.camunda.zeebe.operate.exporter.handlers.UserTaskHandler;
import io.camunda.zeebe.operate.exporter.handlers.VariableHandler;
import io.camunda.zeebe.operate.exporter.util.XMLUtil;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OperateElasticsearchExporter implements Exporter {

  private Logger log = LoggerFactory.getLogger(getClass().getPackageName());
  private Controller controller;
  private OperateElasticsearchExporterConfiguration configuration;
  private ElasticsearchClient client;
  private ExportBatchWriter writer;
  private ElasticsearchScriptBuilder scriptBuilder;
  private XMLUtil xmlUtil;
  private ObjectMapper objectMapper;

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
    objectMapper = new ObjectMapper();

    client = createClient();

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

  private void validate(final OperateElasticsearchExporterConfiguration configuration) {
    // FIXME
  }

  protected ElasticsearchClient createClient() {
    return new ElasticsearchConnector().elasticsearchClient(configuration.elasticsearch);
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
            new ProcessHandler(
                (ProcessIndex) (new ProcessIndex().setIndexPrefix(indexPrefix)), xmlUtil))
        .withHandler(
            new VariableHandler(
                (VariableTemplate) (new VariableTemplate().setIndexPrefix(indexPrefix)),
                configuration.elasticsearch.getVariableSizeThreshold()))
        .withHandler(
            new SequenceFlowHandler(
                (SequenceFlowTemplate) (new SequenceFlowTemplate().setIndexPrefix(indexPrefix))))
        .withHandler(
            new FlowNodeInstanceProcessInstanceHandler(
                (FlowNodeInstanceTemplate)
                    (new FlowNodeInstanceTemplate().setIndexPrefix(indexPrefix))))
        .withHandler(
            new UserTaskHandler(
                (UserTaskTemplate) (new UserTaskTemplate().setIndexPrefix(indexPrefix)),
                objectMapper))
        .withHandler(
            new FlowNodeInstanceIncidentHandler(
                (FlowNodeInstanceTemplate)
                    (new FlowNodeInstanceTemplate().setIndexPrefix(indexPrefix))))
        .build();
  }

  private static final class ElasticsearchRecordFilter implements Context.RecordFilter {
    private static final List<ValueType> VALUE_TYPES_2_IMPORT =
        List.of(
            PROCESS, VARIABLE, PROCESS_INSTANCE, USER_TASK, INCIDENT
            //            DECISION,
            //            DECISION_REQUIREMENTS,
            //            DECISION_EVALUATION,
            //            JOB,
            //            VARIABLE_DOCUMENT,
            //            PROCESS_MESSAGE_SUBSCRIPTION,
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
