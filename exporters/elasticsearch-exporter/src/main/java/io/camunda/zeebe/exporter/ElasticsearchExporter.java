/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter;

import io.camunda.zeebe.exporter.ElasticsearchExporterConfiguration.IndexConfiguration;
import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.ExporterException;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElasticsearchExporter implements Exporter {

  public static final String ZEEBE_RECORD_TEMPLATE_JSON = "/zeebe-record-template.json";

  // by default, the bulk request may not be bigger than 100MB
  private static final int RECOMMENDED_MAX_BULK_MEMORY_LIMIT = 100 * 1024 * 1024;

  private Logger log = LoggerFactory.getLogger(getClass().getPackageName());
  private Controller controller;

  private ElasticsearchExporterConfiguration configuration;

  private ElasticsearchClient client;

  private long lastPosition = -1;
  private boolean indexTemplatesCreated;

  @Override
  public void configure(final Context context) {
    log = context.getLogger();
    configuration =
        context.getConfiguration().instantiate(ElasticsearchExporterConfiguration.class);
    log.debug("Exporter configured with {}", configuration);

    validate(configuration);

    context.setFilter(new ElasticsearchRecordFilter(configuration));
  }

  @Override
  public void open(final Controller controller) {
    this.controller = controller;
    client = createClient();

    scheduleDelayedFlush();
    log.info("Exporter opened");
  }

  @Override
  public void close() {

    try {
      flush();
    } catch (final Exception e) {
      log.warn("Failed to flush records before closing exporter.", e);
    }

    try {
      client.close();
    } catch (final Exception e) {
      log.warn("Failed to close elasticsearch client", e);
    }

    log.info("Exporter closed");
  }

  @Override
  public void export(final Record<?> record) {
    if (!indexTemplatesCreated) {
      createIndexTemplates();
    }

    client.index(record);
    lastPosition = record.getPosition();

    if (client.shouldFlush()) {
      flush();
    }
  }

  private void validate(final ElasticsearchExporterConfiguration configuration) {
    if (configuration.index.prefix != null && configuration.index.prefix.contains("_")) {
      throw new ExporterException(
          String.format(
              "Elasticsearch prefix must not contain underscore. Current value: %s",
              configuration.index.prefix));
    }

    if (configuration.bulk.memoryLimit > RECOMMENDED_MAX_BULK_MEMORY_LIMIT) {
      log.warn(
          "The bulk memory limit is set to more than {} bytes. It is recommended to set the limit between 5 to 15 MB.",
          RECOMMENDED_MAX_BULK_MEMORY_LIMIT);
    }

    final Integer numberOfShards = configuration.index.getNumberOfShards();
    if (numberOfShards != null && numberOfShards < 1) {
      throw new ExporterException(
          String.format(
              "Elasticsearch numberOfShards must be >= 1. Current value: %d", numberOfShards));
    }

    final Integer numberOfReplicas = configuration.index.getNumberOfReplicas();
    if (numberOfReplicas != null && numberOfReplicas < 0) {
      throw new ExporterException(
          String.format(
              "Elasticsearch numberOfReplicas must be >= 0. Current value: %d", numberOfReplicas));
    }
  }

  protected ElasticsearchClient createClient() {
    return new ElasticsearchClient(configuration);
  }

  private void flushAndReschedule() {
    try {
      flush();
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
    client.flush();
    controller.updateLastExportedRecordPosition(lastPosition);
  }

  private void createIndexTemplates() {
    final IndexConfiguration index = configuration.index;

    if (index.createTemplate) {
      createComponentTemplate();

      if (index.deployment) {
        createValueIndexTemplate(ValueType.DEPLOYMENT);
      }
      if (index.process) {
        createValueIndexTemplate(ValueType.PROCESS);
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
      if (index.processInstance) {
        createValueIndexTemplate(ValueType.PROCESS_INSTANCE);
      }
      if (index.processInstanceBatch) {
        createValueIndexTemplate(ValueType.PROCESS_INSTANCE_BATCH);
      }
      if (index.processInstanceCreation) {
        createValueIndexTemplate(ValueType.PROCESS_INSTANCE_CREATION);
      }
      if (index.processMessageSubscription) {
        createValueIndexTemplate(ValueType.PROCESS_MESSAGE_SUBSCRIPTION);
      }
      if (index.decisionRequirements) {
        createValueIndexTemplate(ValueType.DECISION_REQUIREMENTS);
      }
      if (index.decision) {
        createValueIndexTemplate(ValueType.DECISION);
      }
      if (index.decisionEvaluation) {
        createValueIndexTemplate(ValueType.DECISION_EVALUATION);
      }
    }

    indexTemplatesCreated = true;
  }

  private void createComponentTemplate() {
    final String templateName = configuration.index.prefix;
    final String filename = ZEEBE_RECORD_TEMPLATE_JSON;
    if (!client.createComponentTemplate(templateName, filename)) {
      log.warn("Put index template {} from file {} was not acknowledged", templateName, filename);
    }
  }

  private void createValueIndexTemplate(final ValueType valueType) {
    if (!client.putIndexTemplate(valueType)) {
      log.warn("Put index template for value type {} was not acknowledged", valueType);
    }
  }

  private static class ElasticsearchRecordFilter implements Context.RecordFilter {

    private final ElasticsearchExporterConfiguration configuration;

    ElasticsearchRecordFilter(final ElasticsearchExporterConfiguration configuration) {
      this.configuration = configuration;
    }

    @Override
    public boolean acceptType(final RecordType recordType) {
      return configuration.shouldIndexRecordType(recordType);
    }

    @Override
    public boolean acceptValue(final ValueType valueType) {
      return configuration.shouldIndexValueType(valueType);
    }
  }
}
