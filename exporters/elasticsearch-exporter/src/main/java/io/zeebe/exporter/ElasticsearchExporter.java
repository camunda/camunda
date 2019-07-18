/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.exporter;

import io.zeebe.exporter.ElasticsearchExporterConfiguration.IndexConfiguration;
import io.zeebe.exporter.api.Exporter;
import io.zeebe.exporter.api.context.Context;
import io.zeebe.exporter.api.context.Controller;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.ValueType;
import java.time.Duration;
import org.slf4j.Logger;

public class ElasticsearchExporter implements Exporter {

  public static final String ZEEBE_RECORD_TEMPLATE_JSON = "/zeebe-record-template.json";

  private Logger log;
  private Controller controller;

  private ElasticsearchExporterConfiguration configuration;

  private ElasticsearchClient client;

  private long lastPosition = -1;
  private boolean indexTemplatesCreated;

  @Override
  public void configure(Context context) {
    log = context.getLogger();
    configuration =
        context.getConfiguration().instantiate(ElasticsearchExporterConfiguration.class);
    log.debug("Exporter configured with {}", configuration);

    context.setFilter(new ElasticsearchRecordFilter(configuration));
  }

  @Override
  public void open(Controller controller) {
    this.controller = controller;
    client = createClient();

    scheduleDelayedFlush();
    log.info("Exporter opened");
  }

  @Override
  public void close() {
    flush();

    try {
      client.close();
    } catch (Exception e) {
      log.warn("Failed to close elasticsearch client", e);
    }
    log.info("Exporter closed");
  }

  @Override
  public void export(Record record) {
    if (!indexTemplatesCreated) {
      createIndexTemplates();
    }

    client.index(record);
    lastPosition = record.getPosition();

    if (client.shouldFlush()) {
      flush();
    }
  }

  protected ElasticsearchClient createClient() {
    return new ElasticsearchClient(configuration, log);
  }

  private void flushAndReschedule() {
    flush();
    scheduleDelayedFlush();
  }

  private void scheduleDelayedFlush() {
    controller.scheduleTask(Duration.ofSeconds(configuration.bulk.delay), this::flushAndReschedule);
  }

  private void flush() {
    if (client.flush()) {
      controller.updateLastExportedRecordPosition(lastPosition);
    } else {
      log.warn("Failed to flush bulk completely");
    }
  }

  private void createIndexTemplates() {
    final IndexConfiguration index = configuration.index;

    if (index.createTemplate) {
      createRootIndexTemplate();

      if (index.deployment) {
        createValueIndexTemplate(ValueType.DEPLOYMENT);
      }
      if (index.error) {
        createValueIndexTemplate(ValueType.ERROR);
      }
      if (index.incident) {
        createValueIndexTemplate(ValueType.INCIDENT);
      }
      if (index.job) {
        createValueIndexTemplate(ValueType.JOB);
      }
      if (index.jobBatch) {
        createValueIndexTemplate(ValueType.JOB_BATCH);
      }
      if (index.message) {
        createValueIndexTemplate(ValueType.MESSAGE);
      }
      if (index.messageSubscription) {
        createValueIndexTemplate(ValueType.MESSAGE_SUBSCRIPTION);
      }
      if (index.variable) {
        createValueIndexTemplate(ValueType.VARIABLE);
      }
      if (index.variableDocument) {
        createValueIndexTemplate(ValueType.VARIABLE_DOCUMENT);
      }
      if (index.workflowInstance) {
        createValueIndexTemplate(ValueType.WORKFLOW_INSTANCE);
      }
      if (index.workflowInstanceCreation) {
        createValueIndexTemplate(ValueType.WORKFLOW_INSTANCE_CREATION);
      }
      if (index.workflowInstanceSubscription) {
        createValueIndexTemplate(ValueType.WORKFLOW_INSTANCE_SUBSCRIPTION);
      }
    }

    indexTemplatesCreated = true;
  }

  private void createRootIndexTemplate() {
    final String templateName = configuration.index.prefix;
    final String filename = ZEEBE_RECORD_TEMPLATE_JSON;
    if (!client.putIndexTemplate(templateName, filename, "-")) {
      log.warn("Put index template {} from file {} was not acknowledged", templateName, filename);
    }
  }

  private void createValueIndexTemplate(final ValueType valueType) {
    if (!client.putIndexTemplate(valueType)) {
      log.warn("Put index template for value type {} was not acknowledged", valueType);
    }
  }

  private class ElasticsearchRecordFilter implements Context.RecordFilter {

    private final ElasticsearchExporterConfiguration configuration;

    ElasticsearchRecordFilter(ElasticsearchExporterConfiguration configuration) {
      this.configuration = configuration;
    }

    @Override
    public boolean acceptType(RecordType recordType) {
      return configuration.shouldIndexRecordType(recordType);
    }

    @Override
    public boolean acceptValue(ValueType valueType) {
      return configuration.shouldIndexValueType(valueType);
    }
  }
}
