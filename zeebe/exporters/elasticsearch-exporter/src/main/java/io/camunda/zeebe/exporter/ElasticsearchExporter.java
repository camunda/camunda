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
import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.ExporterException;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.util.SemanticVersion;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
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
  private ElasticsearchExporterSchemaManager schemaManager;

  private long lastPosition = -1;

  @Override
  public void configure(final Context context) {
    log = context.getLogger();
    configuration =
        context.getConfiguration().instantiate(ElasticsearchExporterConfiguration.class);
    log.debug("Exporter configured with {}", configuration);

    validate(configuration);
    pluginRepository.load(configuration.getInterceptorPlugins());

    context.setFilter(new ElasticsearchRecordFilter(configuration));
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
    schemaManager = new ElasticsearchExporterSchemaManager(client, configuration);
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

    if (!shouldExportRecord(record)) {
      // ignore the record but still update the last exported position
      // so that we don't block compaction. Don't update the controller yet, this needs to be done
      // on the next flush.
      lastPosition = record.getPosition();
      return;
    }
    schemaManager.createSchema(record.getBrokerVersion());

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

    final int priority = configuration.index.getTemplatePriority();
    if (priority < 0) {
      throw new ExporterException(
          "Elasticsearch index template priority must be >= 0. Current value: %d"
              .formatted(priority));
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

  /**
   * Determine whether a record should be exported or not. For Camunda 8.8 we require Optimize
   * records to be exported, or if the configuration explicitly enables the export of all records
   * {@link ElasticsearchExporterConfiguration#includeEnabledRecords}. For past versions, we
   * continue to export all records.
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

  private SemanticVersion getVersion(final String version) {
    return SemanticVersion.parse(version)
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Unsupported record broker version: ["
                        + version
                        + "] Must be a semantic version."));
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
