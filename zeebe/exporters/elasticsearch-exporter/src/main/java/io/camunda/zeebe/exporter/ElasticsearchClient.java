/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter;

import co.elastic.clients.elasticsearch._helpers.bulk.BulkIngester;
import co.elastic.clients.elasticsearch._helpers.bulk.BulkListener;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.exporter.dto.BulkIndexAction;
import io.camunda.zeebe.exporter.dto.PutIndexLifecycleManagementPolicyRequest;
import io.camunda.zeebe.exporter.dto.PutIndexLifecycleManagementPolicyRequest.Actions;
import io.camunda.zeebe.exporter.dto.PutIndexLifecycleManagementPolicyRequest.Delete;
import io.camunda.zeebe.exporter.dto.PutIndexLifecycleManagementPolicyRequest.DeleteAction;
import io.camunda.zeebe.exporter.dto.PutIndexLifecycleManagementPolicyRequest.Phases;
import io.camunda.zeebe.exporter.dto.PutIndexLifecycleManagementPolicyRequest.Policy;
import io.camunda.zeebe.exporter.dto.Template;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.util.VersionUtil;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer.Sample;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

class ElasticsearchClient implements AutoCloseable {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final co.elastic.clients.elasticsearch.ElasticsearchClient esClient;
  private final ElasticsearchExporterConfiguration configuration;
  private final TemplateReader templateReader;
  private final RecordIndexRouter indexRouter;
  private final BulkIndexRequest bulkIndexRequest;

  private final ElasticsearchMetrics metrics;

  /**
   * Sample to measure the flush latency of the current bulk request.
   *
   * <p>Time of how long an export bulk request is open and collects new records before flushing,
   * meaning latency until the next flush is done.
   */
  private Sample flushLatencyMeasurement;

  ElasticsearchClient(
      final ElasticsearchExporterConfiguration configuration, final MeterRegistry meterRegistry) {
    this(
        configuration,
        new BulkIndexRequest(),
        RestClientFactory.of(configuration),
        new RecordIndexRouter(configuration.index),
        new TemplateReader(configuration),
        new ElasticsearchMetrics(meterRegistry));
  }

  ElasticsearchClient(
      final ElasticsearchExporterConfiguration configuration,
      final MeterRegistry meterRegistry,
      final co.elastic.clients.elasticsearch.ElasticsearchClient esClient) {
    this(
        configuration,
        new BulkIndexRequest(),
        esClient,
        new RecordIndexRouter(configuration.index),
        new TemplateReader(configuration),
        new ElasticsearchMetrics(meterRegistry));
  }

  ElasticsearchClient(
      final ElasticsearchExporterConfiguration configuration,
      final BulkIndexRequest bulkIndexRequest,
      final co.elastic.clients.elasticsearch.ElasticsearchClient esClient,
      final RecordIndexRouter indexRouter,
      final TemplateReader templateReader,
      final ElasticsearchMetrics metrics) {
    this.configuration = configuration;
    this.bulkIndexRequest = bulkIndexRequest;
    this.esClient = esClient;
    this.indexRouter = indexRouter;
    this.templateReader = templateReader;
    this.metrics = metrics;
  }

  @Override
  public void close() throws IOException {
    esClient._transport().close();
  }

  /**
   * Indexes a record to the batch of records that will be sent to Elasticsearch
   *
   * @param record the record that will be the source of the document
   * @param recordSequence the sequence number of the record
   * @return true if the record was appended to the batch, false if the record is already indexed in
   *     the batch because only one copy of the record is allowed in the batch
   */
  public boolean index(final Record<?> record, final RecordSequence recordSequence) {
    if (bulkIndexRequest.isEmpty()) {
      flushLatencyMeasurement = metrics.startFlushLatencyMeasurement();
    }

    final BulkIndexAction action =
        new BulkIndexAction(
            indexRouter.indexFor(record),
            indexRouter.idFor(record),
            indexRouter.routingFor(record));
    return bulkIndexRequest.index(action, record, recordSequence);
  }

  /**
   * Flushes the bulk request to Elastic, unless it's currently empty.
   *
   * @throws ElasticsearchExporterException if not all items of the bulk were flushed successfully
   */
  public void flush() {
    if (bulkIndexRequest.isEmpty()) {
      return;
    }

    metrics.recordBulkSize(bulkIndexRequest.size());
    metrics.recordBulkMemorySize(bulkIndexRequest.memoryUsageBytes());

    metrics.measureFlushDuration(
        () -> {
          try {
            exportBulk();
            metrics.stopFlushLatencyMeasurement(flushLatencyMeasurement);

            bulkIndexRequest.clear();
          } catch (final ElasticsearchExporterException e) {
            metrics.recordFailedFlush();
            throw e;
          }
        });
  }

  /**
   * Returns whether the exporter should call {@link #flush()} or not.
   *
   * @return true if {@link #flush()} should be called, false otherwise
   */
  public boolean shouldFlush() {
    return bulkIndexRequest.memoryUsageBytes() >= configuration.bulk.memoryLimit
        || bulkIndexRequest.size() >= configuration.bulk.size;
  }

  /**
   * Creates an index template for the given value type, read from the resources.
   *
   * @return true if request was acknowledged
   */
  public boolean putIndexTemplate(final ValueType valueType) {
    return putIndexTemplate(valueType, VersionUtil.getVersionLowerCase());
  }

  public boolean putIndexTemplate(final ValueType valueType, final String version) {
    final String templateName = indexRouter.indexPrefixForValueType(valueType, version);
    final Template template =
        templateReader.readIndexTemplate(
            valueType,
            indexRouter.searchPatternForValueType(valueType, version),
            indexRouter.aliasNameForValueType(valueType));

    return putIndexTemplate(templateName, template);
  }

  /**
   * Creates or updates the component template on the target Elasticsearch. The template is read
   * from {@link TemplateReader#readComponentTemplate()}.
   */
  public boolean putComponentTemplate() {
    final Template template = templateReader.readComponentTemplate();
    return putComponentTemplate(template);
  }

  private void exportBulk() {
    final var operations = bulkIndexRequest.toBulkOperations();
    final var errorCollector = new BulkErrorCollector();

    try (final BulkIngester<Void> ingester =
        BulkIngester.of(
            b ->
                b.client(esClient)
                    .maxOperations(operations.size())
                    .maxSize(configuration.bulk.memoryLimit)
                    .listener(errorCollector))) {
      operations.forEach(ingester::add);
    }

    errorCollector.throwIfError();
  }

  private boolean putIndexTemplate(final String templateName, final Template template) {
    try {
      final var json = MAPPER.writeValueAsString(template);
      final var response =
          esClient
              .indices()
              .putIndexTemplate(b -> b.name(templateName).withJson(new StringReader(json)));
      return response.acknowledged();
    } catch (final IOException e) {
      throw new ElasticsearchExporterException("Failed to put index template", e);
    }
  }

  private boolean putComponentTemplate(final Template template) {
    try {
      final var json = MAPPER.writeValueAsString(template);
      final var componentTemplateName =
          configuration.index.prefix + "-" + VersionUtil.getVersionLowerCase();
      final var response =
          esClient
              .cluster()
              .putComponentTemplate(
                  b -> b.name(componentTemplateName).withJson(new StringReader(json)));
      return response.acknowledged();
    } catch (final IOException e) {
      throw new ElasticsearchExporterException("Failed to put component template", e);
    }
  }

  public boolean putIndexLifecycleManagementPolicy() {
    try {
      final var requestEntity =
          buildPutIndexLifecycleManagementPolicyRequest(configuration.retention.getMinimumAge());
      final var json = MAPPER.writeValueAsString(requestEntity);
      final var response =
          esClient
              .ilm()
              .putLifecycle(
                  b ->
                      b.name(configuration.retention.getPolicyName())
                          .withJson(new StringReader(json)));
      return response.acknowledged();
    } catch (final IOException e) {
      throw new ElasticsearchExporterException(
          "Failed to put index lifecycle management policy", e);
    }
  }

  public boolean bulkPutIndexLifecycleSettings(final String policyName) {
    try {
      // Use withJson to pass the settings as raw JSON because the ES Java client's typed builder
      // skips null values, but ES requires {"index.lifecycle.name": null} to remove a policy.
      final var json =
          policyName != null
              ? "{\"index.lifecycle.name\": \"" + policyName + "\"}"
              : "{\"index.lifecycle.name\": null}";
      final var response =
          esClient
              .indices()
              .putSettings(
                  b ->
                      b.index(configuration.index.prefix + "*")
                          .allowNoIndices(true)
                          .withJson(new StringReader(json)));
      return response.acknowledged();
    } catch (final IOException e) {
      throw new ElasticsearchExporterException("Failed to update indices lifecycle settings", e);
    }
  }

  static PutIndexLifecycleManagementPolicyRequest buildPutIndexLifecycleManagementPolicyRequest(
      final String minimumAge) {
    return new PutIndexLifecycleManagementPolicyRequest(
        new Policy(new Phases(new Delete(minimumAge, new Actions(new DeleteAction())))));
  }

  private static List<String> collectBulkErrors(final BulkResponse bulkResponse) {
    final var collectedErrors = new ArrayList<String>();
    bulkResponse.items().stream()
        .filter(item -> item.error() != null)
        .collect(Collectors.groupingBy(item -> item.error().type()))
        .forEach(
            (errorType, errors) ->
                collectedErrors.add(
                    String.format(
                        "Failed to flush %d item(s) of bulk request [type: %s, reason: %s]",
                        errors.size(), errorType, errors.get(0).error().reason())));
    return collectedErrors;
  }

  private static final class BulkErrorCollector implements BulkListener<Void> {
    private ElasticsearchExporterException error;

    @Override
    public void beforeBulk(
        final long executionId, final BulkRequest request, final List<Void> contexts) {
      // no-op
    }

    @Override
    public void afterBulk(
        final long executionId,
        final BulkRequest request,
        final List<Void> contexts,
        final BulkResponse response) {
      if (response.errors()) {
        error =
            new ElasticsearchExporterException(
                "Failed to flush bulk request: " + collectBulkErrors(response));
      }
    }

    @Override
    public void afterBulk(
        final long executionId,
        final BulkRequest request,
        final List<Void> contexts,
        final Throwable failure) {
      error = new ElasticsearchExporterException("Failed to flush bulk", failure);
    }

    void throwIfError() {
      if (error != null) {
        throw error;
      }
    }
  }
}
