/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.exporter.dto.BulkIndexAction;
import io.camunda.zeebe.exporter.dto.BulkIndexResponse;
import io.camunda.zeebe.exporter.dto.BulkIndexResponse.Error;
import io.camunda.zeebe.exporter.dto.PutIndexTemplateResponse;
import io.camunda.zeebe.exporter.dto.Template;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.prometheus.client.Histogram;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.http.entity.EntityTemplate;
import org.opensearch.client.Request;
import org.opensearch.client.RestClient;

class OpensearchClient implements AutoCloseable {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final RestClient client;
  private final OpensearchExporterConfiguration configuration;
  private final TemplateReader templateReader;
  private final RecordIndexRouter indexRouter;
  private final BulkIndexRequest bulkIndexRequest;

  private OpensearchMetrics metrics;

  OpensearchClient(final OpensearchExporterConfiguration configuration) {
    this(configuration, new BulkIndexRequest());
  }

  OpensearchClient(
      final OpensearchExporterConfiguration configuration,
      final BulkIndexRequest bulkIndexRequest) {
    this(
        configuration,
        bulkIndexRequest,
        RestClientFactory.of(configuration),
        new RecordIndexRouter(configuration.index),
        new TemplateReader(configuration.index),
        null);
  }

  OpensearchClient(
      final OpensearchExporterConfiguration configuration,
      final BulkIndexRequest bulkIndexRequest,
      final RestClient client,
      final RecordIndexRouter indexRouter,
      final TemplateReader templateReader,
      final OpensearchMetrics metrics) {
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

  public void index(final Record<?> record, final RecordSequence recordSequence) {
    if (metrics == null) {
      metrics = new OpensearchMetrics(record.getPartitionId());
    }

    final BulkIndexAction action =
        new BulkIndexAction(
            indexRouter.indexFor(record),
            indexRouter.idFor(record),
            indexRouter.routingFor(record));
    bulkIndexRequest.index(action, record, recordSequence);
  }

  /**
   * Flushes the bulk request to Opensearch, unless it's currently empty.
   *
   * @throws OpensearchExporterException if not all items of the bulk were flushed successfully
   */
  public void flush() {
    if (bulkIndexRequest.isEmpty()) {
      return;
    }

    metrics.recordBulkSize(bulkIndexRequest.size());
    metrics.recordBulkMemorySize(bulkIndexRequest.memoryUsageBytes());

    try (final Histogram.Timer ignored = metrics.measureFlushDuration()) {
      exportBulk();

      // all records where flushed, create new bulk request, otherwise retry next time
      bulkIndexRequest.clear();
    } catch (final OpensearchExporterException e) {
      metrics.recordFailedFlush();
      throw e;
    }
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
    final String templateName = indexRouter.indexPrefixForValueType(valueType);
    final Template template =
        templateReader.readIndexTemplate(
            valueType,
            indexRouter.searchPatternForValueType(valueType),
            indexRouter.aliasNameForValueType(valueType));

    return putIndexTemplate(templateName, template);
  }

  /**
   * Creates or updates the component template on the target Opensearch. The template is read from
   * {@link TemplateReader#readComponentTemplate()}.
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
      throw new OpensearchExporterException("Failed to flush bulk", e);
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

    throw new OpensearchExporterException("Failed to flush bulk request: " + collectedErrors);
  }

  private boolean putIndexTemplate(final String templateName, final Template template) {
    try {
      final var request = new Request("PUT", "/_index_template/" + templateName);
      request.setJsonEntity(MAPPER.writeValueAsString(template));

      final var response = sendRequest(request, PutIndexTemplateResponse.class);
      return response.acknowledged();
    } catch (final IOException e) {
      throw new OpensearchExporterException("Failed to put index template", e);
    }
  }

  private boolean putComponentTemplate(final Template template) {
    try {
      final var request = new Request("PUT", "/_component_template/" + configuration.index.prefix);
      request.setJsonEntity(MAPPER.writeValueAsString(template));

      final var response = sendRequest(request, PutIndexTemplateResponse.class);
      return response.acknowledged();
    } catch (final IOException e) {
      throw new OpensearchExporterException("Failed to put component template", e);
    }
  }

  private <T> T sendRequest(final Request request, final Class<T> responseType) throws IOException {
    final var response = client.performRequest(request);
    // buffer the complete response in memory before parsing it; this will give us a better error
    // message which contains the raw response should the deserialization fail
    final var responseBody = response.getEntity().getContent().readAllBytes();
    return MAPPER.readValue(responseBody, responseType);
  }
}
