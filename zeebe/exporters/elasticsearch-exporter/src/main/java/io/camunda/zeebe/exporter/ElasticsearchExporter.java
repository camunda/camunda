/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.search.connect.plugin.PluginRepository;
import io.camunda.zeebe.exporter.ElasticsearchExporterConfiguration.IndexConfiguration;
import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.ExporterException;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElasticsearchExporter implements Exporter {

  /**
   * Supported pattern for min_age property of ILM, we only support: days, hours, minutes and
   * seconds. Everything below seconds we don't expect as useful.
   *
   * <p>See reference
   * https://www.elastic.co/guide/en/elasticsearch/reference/current/api-conventions.html#time-units
   */
  private static final String PATTERN_MIN_AGE_FORMAT = "^[0-9]+[dhms]$";

  private static final Predicate<String> CHECKER_MIN_AGE =
      Pattern.compile(PATTERN_MIN_AGE_FORMAT).asPredicate();
  // by default, the bulk request may not be bigger than 100MB
  private static final int RECOMMENDED_MAX_BULK_MEMORY_LIMIT = 100 * 1024 * 1024;
  private Logger log = LoggerFactory.getLogger(getClass().getPackageName());
  private final ObjectMapper exporterMetadataObjectMapper = new ObjectMapper();

  private final ElasticsearchExporterMetadata exporterMetadata =
      new ElasticsearchExporterMetadata();
  private final PluginRepository pluginRepository = new PluginRepository();

  private Controller controller;
  private ElasticsearchExporterConfiguration configuration;
  private ElasticsearchClient client;
  private ElasticsearchRecordCounters recordCounters;
  private MeterRegistry registry;

  private long lastPosition = -1;
  private Set<String> indexTemplatesCreated;

  @Override
  public void configure(final Context context) {
    log = context.getLogger();
    configuration =
        context.getConfiguration().instantiate(ElasticsearchExporterConfiguration.class);
    log.debug("Exporter configured with {}", configuration);

    validate(configuration);
    pluginRepository.load(configuration.getInterceptorPlugins());

    context.setFilter(new ElasticsearchRecordFilter(configuration));
    indexTemplatesCreated = new HashSet<>();
    registry = context.getMeterRegistry();
  }

  @Override
  public void open(final Controller controller) {
    this.controller = controller;
    client = createClient();

    recordCounters =
        controller
            .readMetadata()
            .map(this::deserializeExporterMetadata)
            .map(ElasticsearchExporterMetadata::getRecordCountersByValueType)
            .map(ElasticsearchRecordCounters::new)
            .orElse(new ElasticsearchRecordCounters());

    scheduleDelayedFlush();
    log.info("Exporter opened");
  }

  @Override
  public void close() {
    // the client is only created in some lifecycles, so during others (e.g. validation) it may not
    // exist, in which case there's no point flushing or doing anything
    if (client != null) {
      try {
        flush();
        updateLastExportedPosition();
      } catch (final Exception e) {
        log.warn("Failed to flush records before closing exporter.", e);
      }

      try {
        client.close();
      } catch (final Exception e) {
        log.warn("Failed to close elasticsearch client", e);
      }
    }

    try {
      pluginRepository.close();
    } catch (final Exception e) {
      log.warn("Failed to close plugin repository", e);
    }

    log.info("Exporter closed");
  }

  @Override
  public void export(final Record<?> record) {
    if (!indexTemplatesCreated.contains(record.getBrokerVersion())) {
      createIndexTemplates(record.getBrokerVersion());

      updateRetentionPolicyForExistingIndices();
    }

    final var recordSequence = recordCounters.getNextRecordSequence(record);
    final var isRecordIndexedToBatch = client.index(record, recordSequence);
    if (isRecordIndexedToBatch) {
      recordCounters.updateRecordCounters(record, recordSequence);
    }
    lastPosition = record.getPosition();

    if (client.shouldFlush()) {
      flush();
      updateLastExportedPosition();
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

    final String minimumAge = configuration.retention.getMinimumAge();
    if (minimumAge != null && !CHECKER_MIN_AGE.test(minimumAge)) {
      throw new ExporterException(
          String.format(
              "Elasticsearch minimumAge '%s' must match pattern '%s', but didn't.",
              minimumAge, PATTERN_MIN_AGE_FORMAT));
    }

    final String indexSuffixDatePattern = configuration.index.indexSuffixDatePattern;
    try {
      DateTimeFormatter.ofPattern(indexSuffixDatePattern).withZone(ZoneOffset.UTC);
    } catch (final IllegalArgumentException iae) {
      throw new ExporterException(
          String.format(
              "Expected a valid date format pattern for the given elasticsearch indexSuffixDatePattern, but '%s' was not. Examples are: 'yyyy-MM-dd' or 'yyyy-MM-dd_HH'",
              indexSuffixDatePattern),
          iae);
    }
  }

  // TODO: remove this and instead allow client to be inject-able for testing
  protected ElasticsearchClient createClient() {
    return new ElasticsearchClient(
        configuration,
        registry,
        RestClientFactory.of(configuration, pluginRepository.asRequestInterceptor()));
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
    client.flush();
  }

  private void updateLastExportedPosition() {
    exporterMetadata.setRecordCountersByValueType(recordCounters.getRecordCounters());
    final var serializeExporterMetadata = serializeExporterMetadata(exporterMetadata);
    controller.updateLastExportedRecordPosition(lastPosition, serializeExporterMetadata);
  }

  private byte[] serializeExporterMetadata(final ElasticsearchExporterMetadata metadata) {
    try {
      return exporterMetadataObjectMapper.writeValueAsBytes(metadata);
    } catch (final JsonProcessingException e) {
      throw new ElasticsearchExporterException("Failed to serialize exporter metadata", e);
    }
  }

  private ElasticsearchExporterMetadata deserializeExporterMetadata(final byte[] metadata) {
    try {
      return exporterMetadataObjectMapper.readValue(metadata, ElasticsearchExporterMetadata.class);
    } catch (final IOException e) {
      throw new ElasticsearchExporterException("Failed to deserialize exporter metadata", e);
    }
  }

  private void createIndexTemplates(final String version) {
    if (configuration.retention.isEnabled()) {
      createIndexLifecycleManagementPolicy();
    }

    final IndexConfiguration index = configuration.index;

    if (index.createTemplate) {
      createComponentTemplate();

      if (index.deployment) {
        createValueIndexTemplate(ValueType.DEPLOYMENT, version);
      }
      if (index.process) {
        createValueIndexTemplate(ValueType.PROCESS, version);
      }
      if (index.error) {
        createValueIndexTemplate(ValueType.ERROR, version);
      }
      if (index.incident) {
        createValueIndexTemplate(ValueType.INCIDENT, version);
      }
      if (index.job) {
        createValueIndexTemplate(ValueType.JOB, version);
      }
      if (index.jobBatch) {
        createValueIndexTemplate(ValueType.JOB_BATCH, version);
      }
      if (index.message) {
        createValueIndexTemplate(ValueType.MESSAGE, version);
      }
      if (index.messageBatch) {
        createValueIndexTemplate(ValueType.MESSAGE_BATCH, version);
      }
      if (index.messageSubscription) {
        createValueIndexTemplate(ValueType.MESSAGE_SUBSCRIPTION, version);
      }
      if (index.variable) {
        createValueIndexTemplate(ValueType.VARIABLE, version);
      }
      if (index.variableDocument) {
        createValueIndexTemplate(ValueType.VARIABLE_DOCUMENT, version);
      }
      if (index.processInstance) {
        createValueIndexTemplate(ValueType.PROCESS_INSTANCE, version);
      }
      if (index.processInstanceBatch) {
        createValueIndexTemplate(ValueType.PROCESS_INSTANCE_BATCH, version);
      }
      if (index.processInstanceCreation) {
        createValueIndexTemplate(ValueType.PROCESS_INSTANCE_CREATION, version);
      }
      if (index.processInstanceModification) {
        createValueIndexTemplate(ValueType.PROCESS_INSTANCE_MODIFICATION, version);
      }
      if (index.processMessageSubscription) {
        createValueIndexTemplate(ValueType.PROCESS_MESSAGE_SUBSCRIPTION, version);
      }
      if (index.decisionRequirements) {
        createValueIndexTemplate(ValueType.DECISION_REQUIREMENTS, version);
      }
      if (index.decision) {
        createValueIndexTemplate(ValueType.DECISION, version);
      }
      if (index.decisionEvaluation) {
        createValueIndexTemplate(ValueType.DECISION_EVALUATION, version);
      }
      if (index.checkpoint) {
        createValueIndexTemplate(ValueType.CHECKPOINT, version);
      }
      if (index.timer) {
        createValueIndexTemplate(ValueType.TIMER, version);
      }
      if (index.messageStartEventSubscription) {
        createValueIndexTemplate(ValueType.MESSAGE_START_EVENT_SUBSCRIPTION, version);
      }
      if (index.processEvent) {
        createValueIndexTemplate(ValueType.PROCESS_EVENT, version);
      }
      if (index.deploymentDistribution) {
        createValueIndexTemplate(ValueType.DEPLOYMENT_DISTRIBUTION, version);
      }
      if (index.escalation) {
        createValueIndexTemplate(ValueType.ESCALATION, version);
      }
      if (index.signal) {
        createValueIndexTemplate(ValueType.SIGNAL, version);
      }
      if (index.signalSubscription) {
        createValueIndexTemplate(ValueType.SIGNAL_SUBSCRIPTION, version);
      }
      if (index.resourceDeletion) {
        createValueIndexTemplate(ValueType.RESOURCE_DELETION, version);
      }
      if (index.commandDistribution) {
        createValueIndexTemplate(ValueType.COMMAND_DISTRIBUTION, version);
      }
      if (index.form) {
        createValueIndexTemplate(ValueType.FORM, version);
      }
      if (index.userTask) {
        createValueIndexTemplate(ValueType.USER_TASK, version);
      }
      if (index.processInstanceMigration) {
        createValueIndexTemplate(ValueType.PROCESS_INSTANCE_MIGRATION, version);
      }
      if (index.compensationSubscription) {
        createValueIndexTemplate(ValueType.COMPENSATION_SUBSCRIPTION, version);
      }
      if (index.messageCorrelation) {
        createValueIndexTemplate(ValueType.MESSAGE_CORRELATION, version);
      }
    }

    indexTemplatesCreated.add(version);
  }

  private void createIndexLifecycleManagementPolicy() {
    if (!client.putIndexLifecycleManagementPolicy()) {
      log.warn(
          "Failed to acknowledge the creation or update of the Index Lifecycle Management Policy");
    }
  }

  private void createComponentTemplate() {
    if (!client.putComponentTemplate()) {
      log.warn("Failed to acknowledge the creation or update of the component template");
    }
  }

  private void createValueIndexTemplate(final ValueType valueType, final String version) {
    if (!client.putIndexTemplate(valueType, version)) {
      log.warn(
          "Failed to acknowledge the creation or update of the index template for value type {}",
          valueType);
    }
  }

  private void updateRetentionPolicyForExistingIndices() {
    final boolean acknowledged;
    if (configuration.retention.isEnabled()) {
      acknowledged = client.bulkPutIndexLifecycleSettings(configuration.retention.getPolicyName());
    } else {
      acknowledged = client.bulkPutIndexLifecycleSettings(null);
    }

    if (!acknowledged) {
      log.warn("Failed to acknowledge the the update of retention policy for existing indices");
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
