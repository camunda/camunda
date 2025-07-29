/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.exporter.dto.BulkIndexAction;
import io.camunda.zeebe.exporter.dto.BulkIndexResponse;
import io.camunda.zeebe.exporter.dto.BulkIndexResponse.Error;
import io.camunda.zeebe.exporter.dto.PutIndexLifecycleManagementPolicyRequest;
import io.camunda.zeebe.exporter.dto.PutIndexLifecycleManagementPolicyRequest.Actions;
import io.camunda.zeebe.exporter.dto.PutIndexLifecycleManagementPolicyRequest.Delete;
import io.camunda.zeebe.exporter.dto.PutIndexLifecycleManagementPolicyRequest.DeleteAction;
import io.camunda.zeebe.exporter.dto.PutIndexLifecycleManagementPolicyRequest.Phases;
import io.camunda.zeebe.exporter.dto.PutIndexLifecycleManagementPolicyRequest.Policy;
import io.camunda.zeebe.exporter.dto.PutIndexLifecycleManagementPolicyResponse;
import io.camunda.zeebe.exporter.dto.PutIndexSettingsRequest;
import io.camunda.zeebe.exporter.dto.PutIndexSettingsRequest.Index;
import io.camunda.zeebe.exporter.dto.PutIndexSettingsRequest.Lifecycle;
import io.camunda.zeebe.exporter.dto.PutIndexSettingsResponse;
import io.camunda.zeebe.exporter.dto.PutIndexTemplateResponse;
import io.camunda.zeebe.exporter.dto.Template;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.util.VersionUtil;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer.Sample;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.http.entity.EntityTemplate;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RestClient;

class ElasticsearchClient implements AutoCloseable {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final RestClient client;
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
      final RestClient restClient) {
    this(
        configuration,
        new BulkIndexRequest(),
        restClient,
        new RecordIndexRouter(configuration.index),
        new TemplateReader(configuration),
        new ElasticsearchMetrics(meterRegistry));
  }

  ElasticsearchClient(
      final ElasticsearchExporterConfiguration configuration,
      final BulkIndexRequest bulkIndexRequest,
      final RestClient client,
      final RecordIndexRouter indexRouter,
      final TemplateReader templateReader,
      final ElasticsearchMetrics metrics) {
    this.configuration = configuration;
    this.bulkIndexRequest = bulkIndexRequest;
    this.client = client;
    this.indexRouter = indexRouter;
    this.templateReader = templateReader;
    this.metrics = metrics;
  }

  @Override
  public void close() throws IOException {
    client.close();
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
    final BulkIndexResponse response;
    try {
      final var request = new Request("POST", "/_bulk");
      final var body = new EntityTemplate(bulkIndexRequest);
      body.setContentType("application/x-ndjson");
      request.setEntity(body);

      response = sendRequest(request, BulkIndexResponse.class);
    } catch (final IOException e) {
      throw new ElasticsearchExporterException("Failed to flush bulk", e);
    }

    if (response.errors()) {
      throwCollectedBulkError(response);
    }
  }

  private void throwCollectedBulkError(final BulkIndexResponse bulkResponse) {
    final var collectedErrors = new ArrayList<String>();
    bulkResponse.items().stream()
        .flatMap(item -> Optional.ofNullable(item.index()).stream())
        .flatMap(index -> Optional.ofNullable(index.error()).stream())
        .collect(Collectors.groupingBy(Error::type))
        .forEach(
            (errorType, errors) ->
                collectedErrors.add(
                    String.format(
                        "Failed to flush %d item(s) of bulk request [type: %s, reason: %s]",
                        errors.size(), errorType, errors.get(0).reason())));

    throw new ElasticsearchExporterException("Failed to flush bulk request: " + collectedErrors);
  }

  private boolean putIndexTemplate(final String templateName, final Template template) {
    try {
      final var request = new Request("PUT", "/_index_template/" + templateName);
      request.setJsonEntity(MAPPER.writeValueAsString(template));

      final var response = sendRequest(request, PutIndexTemplateResponse.class);
      return response.acknowledged();
    } catch (final IOException e) {
      throw new ElasticsearchExporterException("Failed to put index template", e);
    }
  }

  private boolean putComponentTemplate(final Template template) {
    try {
      final var request =
          new Request(
              "PUT",
              "/_component_template/"
                  + configuration.index.prefix
                  + "-"
                  + VersionUtil.getVersionLowerCase());
      request.setJsonEntity(MAPPER.writeValueAsString(template));

      final var response = sendRequest(request, PutIndexTemplateResponse.class);
      return response.acknowledged();
    } catch (final IOException e) {
      throw new ElasticsearchExporterException("Failed to put component template", e);
    }
  }

  public boolean putIndexLifecycleManagementPolicy() {
    try {
      final var request =
          new Request("PUT", "/_ilm/policy/" + configuration.retention.getPolicyName());
      final var requestEntity =
          buildPutIndexLifecycleManagementPolicyRequest(configuration.retention.getMinimumAge());
      request.setJsonEntity(MAPPER.writeValueAsString(requestEntity));
      final var response = sendRequest(request, PutIndexLifecycleManagementPolicyResponse.class);
      return response.acknowledged();
    } catch (final IOException e) {
      throw new ElasticsearchExporterException(
          "Failed to put index lifecycle management policy", e);
    }
  }

  public boolean bulkPutIndexLifecycleSettings(final String policyName) {
    try {
      final var request =
          new Request(
              "PUT", "/" + configuration.index.prefix + "*/_settings?allow_no_indices=true");
      final var requestEntity = new PutIndexSettingsRequest(new Index(new Lifecycle(policyName)));
      request.setJsonEntity(MAPPER.writeValueAsString(requestEntity));
      final var response = sendRequest(request, PutIndexSettingsResponse.class);
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

  private <T> T sendRequest(final Request request, final Class<T> responseType) throws IOException {
    final var response = client.performRequest(request);
    // buffer the complete response in memory before parsing it; this will give us a better error
    // message which contains the raw response should the deserialization fail
    final var responseBody = response.getEntity().getContent().readAllBytes();
    return MAPPER.readValue(responseBody, responseType);
  }
}
