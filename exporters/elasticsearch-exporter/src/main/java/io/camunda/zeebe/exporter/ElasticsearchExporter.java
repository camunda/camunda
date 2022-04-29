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
import io.camunda.zeebe.protocol.record.ValueTypeMapping;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElasticsearchExporter implements Exporter {

  // by default, the bulk request may not be bigger than 100MB
  private static final int RECOMMENDED_MAX_BULK_MEMORY_LIMIT = 100 * 1024 * 1024;

  private Logger log = LoggerFactory.getLogger(getClass().getPackageName());
  private Controller controller;

  private ElasticsearchExporterConfiguration configuration;

  private ElasticClient client;

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

  protected ElasticClient createClient() {
    return new ElasticClient(configuration);
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

      ValueTypeMapping.getAcceptedValueTypes().stream()
          .filter(configuration::shouldIndexValueType)
          .forEach(this::createValueIndexTemplate);
    }

    indexTemplatesCreated = true;
  }

  private void createComponentTemplate() {
    if (!client.putComponentTemplate()) {
      log.warn("Failed to put component template; request was not acknowledged");
    }
  }

  private void createValueIndexTemplate(final ValueType valueType) {
    if (!client.putIndexTemplate(valueType)) {
      log.warn(
          "Failed to put index template for value type {}; request was not acknowledged",
          valueType);
    }
  }

  record ElasticsearchRecordFilter(ElasticsearchExporterConfiguration configuration)
      implements Context.RecordFilter {

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
