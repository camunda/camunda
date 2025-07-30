/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.opensearch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.search.connect.plugin.PluginRepository;
import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.ExporterException;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.exporter.opensearch.OpensearchExporterConfiguration.IndexConfiguration;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.util.SemanticVersion;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpensearchExporter implements Exporter {

  // by default, the bulk request may not be bigger than 100MB
  private static final int RECOMMENDED_MAX_BULK_MEMORY_LIMIT = 100 * 1024 * 1024;

  private Logger log = LoggerFactory.getLogger(getClass().getPackageName());
  private final ObjectMapper exporterMetadataObjectMapper = new ObjectMapper();

  private final OpensearchExporterMetadata exporterMetadata = new OpensearchExporterMetadata();
  private final PluginRepository pluginRepository = new PluginRepository();

  private Controller controller;
  private OpensearchExporterConfiguration configuration;
  private OpensearchClient client;
  private OpensearchRecordCounters recordCounters;
  private MeterRegistry meterRegistry;

  private long lastPosition = -1;
  private Set<String> indexTemplatesCreated;

  @Override
  public void configure(final Context context) {
    log = context.getLogger();
    configuration = context.getConfiguration().instantiate(OpensearchExporterConfiguration.class);
    log.debug("Exporter configured with {}", configuration);

    validate(configuration);
    pluginRepository.load(configuration.getInterceptorPlugins());

    context.setFilter(new OpensearchRecordFilter(configuration));
    indexTemplatesCreated = new HashSet<>();
    meterRegistry = context.getMeterRegistry();
  }

  @Override
  public void open(final Controller controller) {
    this.controller = controller;
    client = createClient();

    recordCounters =
        controller
            .readMetadata()
            .map(this::deserializeExporterMetadata)
            .map(OpensearchExporterMetadata::getRecordCountersByValueType)
            .map(OpensearchRecordCounters::new)
            .orElse(new OpensearchRecordCounters());

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
        log.warn("Failed to close opensearch client", e);
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

    if (!shouldExportRecord(record)) {
      // ignore the record but still update the last exported position
      // so that we don't block compaction. Don't update the controller yet, this needs to be done
      // on the next flush.
      lastPosition = record.getPosition();
      return;
    }

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

  /**
   * Determine whether a record should be exported or not. For Camunda 8.8 we require Optimize
   * records to be exported, or if the configuration explicitly enables the export of all records
   * {@link OpensearchExporterConfiguration#includeEnabledRecords}. For past versions, we continue
   * to export all records.
   *
   * @param record The record to check
   * @return Whether the record should be exported or not
   */
  private boolean shouldExportRecord(final Record<?> record) {
    final var recordVersion = getVersion(record.getBrokerVersion());
    if (configuration.getIsIncludeEnabledRecords()
        || (recordVersion.major() == 8 && recordVersion.minor() < 8)) {
      return true;
    }
    return configuration.shouldIndexRequiredValueType(record.getValueType());
  }

  private void validate(final OpensearchExporterConfiguration configuration) {
    if (configuration.index.prefix != null && configuration.index.prefix.contains("_")) {
      throw new ExporterException(
          String.format(
              "Opensearch prefix must not contain underscore. Current value: %s",
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
              "Opensearch numberOfShards must be >= 1. Current value: %d", numberOfShards));
    }

    final Integer numberOfReplicas = configuration.index.getNumberOfReplicas();
    if (numberOfReplicas != null && numberOfReplicas < 0) {
      throw new ExporterException(
          String.format(
              "Opensearch numberOfReplicas must be >= 0. Current value: %d", numberOfReplicas));
    }

    final int priority = configuration.index.getPriority();
    if (priority < 0) {
      throw new ExporterException(
          "Opensearch index template priority must be >= 0. Current value: %d".formatted(priority));
    }
  }

  // TODO: remove this and instead allow client to be inject-able for testing
  protected OpensearchClient createClient() {
    return new OpensearchClient(
        configuration,
        meterRegistry,
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

  private byte[] serializeExporterMetadata(final OpensearchExporterMetadata metadata) {
    try {
      return exporterMetadataObjectMapper.writeValueAsBytes(metadata);
    } catch (final JsonProcessingException e) {
      throw new OpensearchExporterException("Failed to serialize exporter metadata", e);
    }
  }

  private OpensearchExporterMetadata deserializeExporterMetadata(final byte[] metadata) {
    try {
      return exporterMetadataObjectMapper.readValue(metadata, OpensearchExporterMetadata.class);
    } catch (final IOException e) {
      throw new OpensearchExporterException("Failed to deserialize exporter metadata", e);
    }
  }

  private void createIndexTemplates(final String version) {
    if (configuration.retention.isEnabled()) {
      createIndexStateManagementPolicy();
    } else {
      deleteIndexStateManagementPolicy();
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
      if (index.adHocSubProcessInstruction) {
        createValueIndexTemplate(ValueType.AD_HOC_SUB_PROCESS_INSTRUCTION, version);
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
      if (index.asyncRequest) {
        createValueIndexTemplate(ValueType.ASYNC_REQUEST, version);
      }
      if (index.runtimeInstruction) {
        createValueIndexTemplate(ValueType.RUNTIME_INSTRUCTION, version);
      }
    }

    indexTemplatesCreated.add(version);
  }

  private void createIndexStateManagementPolicy() {
    final var policyOptional = client.getIndexStateManagementPolicy();

    // Create the policy if it doesn't exist yet
    if (policyOptional.isEmpty()) {
      if (!client.createIndexStateManagementPolicy()) {
        log.warn("Failed to acknowledge the creation of the Index State Management Policy");
      }
      return;
    }

    // Update the policy if it exists and is different from the configuration
    final var policy = policyOptional.get();
    if (!policy.equalsConfiguration(configuration)) {
      if (!client.updateIndexStateManagementPolicy(policy.seqNo(), policy.primaryTerm())) {
        log.warn("Failed to acknowledge the update of the Index State Management Policy");
      }
    }
  }

  private void deleteIndexStateManagementPolicy() {
    final var policyOptional = client.getIndexStateManagementPolicy();
    if (policyOptional.isEmpty()) {
      return;
    }

    if (!client.deleteIndexStateManagementPolicy()) {
      log.warn("Failed to acknowledge the deletion of the Index State Management Policy");
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
    final boolean successful;
    if (configuration.retention.isEnabled()) {
      successful = client.bulkAddISMPolicyToAllZeebeIndices();
    } else {
      successful = client.bulkRemoveISMPolicyToAllZeebeIndices();
    }

    if (!successful) {
      log.warn("Failed to acknowledge the the update of retention policy for existing indices");
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

  private static class OpensearchRecordFilter implements Context.RecordFilter {

    private final OpensearchExporterConfiguration configuration;

    OpensearchRecordFilter(final OpensearchExporterConfiguration configuration) {
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
