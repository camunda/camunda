/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.exporter.dto.BulkItemError;
import io.camunda.zeebe.exporter.dto.BulkRequestAction;
import io.camunda.zeebe.exporter.dto.BulkRequestAction.IndexAction;
import io.camunda.zeebe.exporter.dto.BulkResponse;
import io.camunda.zeebe.exporter.dto.PutIndexTemplateResponse;
import io.camunda.zeebe.exporter.dto.Template;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.prometheus.client.Histogram;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;

/**
 * Thin wrapper around the actual underlying {@link RestClient} which manages all interactions with
 * the actual Elasticsearch instance.
 */
final class ElasticClient implements AutoCloseable {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final IndexRouter indexRouter;
  private final RestClient client;
  private final TemplateReader templateReader;
  private final ElasticsearchExporterConfiguration configuration;
  private final List<String> bulkRequest;

  private ElasticsearchMetrics metrics;

  ElasticClient(final ElasticsearchExporterConfiguration configuration) {
    this(
        configuration,
        new ArrayList<>(),
        new RestClientFactory(configuration).createRestClient(),
        new IndexRouter(configuration.index),
        new TemplateReader(configuration.index));
  }

  ElasticClient(
      final ElasticsearchExporterConfiguration configuration, final List<String> bulkRequest) {
    this(configuration, bulkRequest, new RestClientFactory(configuration).createRestClient());
  }

  ElasticClient(
      final ElasticsearchExporterConfiguration configuration,
      final List<String> bulkRequest,
      final RestClient restClient) {
    this(
        configuration,
        bulkRequest,
        restClient,
        new IndexRouter(configuration.index),
        new TemplateReader(configuration.index));
  }

  ElasticClient(
      final ElasticsearchExporterConfiguration configuration,
      final List<String> bulkRequest,
      final RestClient client,
      final IndexRouter indexRouter,
      final TemplateReader templateReader) {
    this.configuration = configuration;
    this.client = client;
    this.indexRouter = indexRouter;
    this.templateReader = templateReader;
    this.bulkRequest = bulkRequest;
  }

  @Override
  public void close() throws IOException {
    client.close();
  }

  public void index(final Record<?> record) {
    if (metrics == null) {
      metrics = new ElasticsearchMetrics(record.getPartitionId());
    }

    final BulkRequestAction action =
        new BulkRequestAction(
            new IndexAction(
                indexRouter.indexFor(record),
                indexRouter.idFor(record),
                String.valueOf(record.getPartitionId())));
    bulk(action, record);
  }

  /**
   * @throws ElasticsearchExporterException if not all items of the bulk were flushed successfully
   */
  public void flush() {
    if (bulkRequest.isEmpty()) {
      return;
    }

    final int bulkSize = bulkRequest.size();
    metrics.recordBulkSize(bulkSize);

    final var bulkMemorySize = getBulkMemorySize();
    metrics.recordBulkMemorySize(bulkMemorySize);

    try (final Histogram.Timer ignored = metrics.measureFlushDuration()) {
      exportBulk();
      // all records where flushed, create new bulk request, otherwise retry next time
      bulkRequest.clear();
    } catch (final ElasticsearchExporterException e) {
      metrics.recordFailedFlush();
      throw e;
    }
  }

  public boolean shouldFlush() {
    return bulkRequest.size() >= configuration.bulk.size
        || getBulkMemorySize() >= configuration.bulk.memoryLimit;
  }

  /**
   * @return true if request was acknowledged
   */
  public boolean putIndexTemplate(final ValueType valueType) {
    final String templateName = indexRouter.indexPrefixForValueType(valueType);
    final String aliasName = indexRouter.aliasNameForValueType(valueType);
    final String searchPattern = indexRouter.searchPatternForValueType(valueType);
    final Template template = templateReader.readIndexTemplate(valueType, searchPattern, aliasName);

    return putIndexTemplate(templateName, template);
  }

  /**
   * @return true if request was acknowledged
   */
  public boolean putComponentTemplate() {
    final Template template = templateReader.readComponentTemplate();
    return putComponentTemplate(configuration.index.prefix, template);
  }

  // visible for testing - otherwise you need to index a record to create the metrics
  ElasticClient setMetrics(final ElasticsearchMetrics metrics) {
    this.metrics = metrics;
    return this;
  }

  private void bulk(final BulkRequestAction action, final Record<?> record) {
    final String bulkRequestItem;

    try {
      final String serializedAction = MAPPER.writeValueAsString(action);
      bulkRequestItem = serializedAction + "\n" + MAPPER.writeValueAsString(record);
    } catch (final IOException e) {
      throw new ElasticsearchExporterException(
          "Failed to serialize bulk request action to JSON", e);
    }

    // don't re-append when retrying same record, to avoid OOM
    if (bulkRequest.isEmpty() || !bulkRequest.get(bulkRequest.size() - 1).equals(bulkRequestItem)) {
      bulkRequest.add(bulkRequestItem);
    }
  }

  private void exportBulk() {
    final Response httpResponse;
    try {
      final var request = new Request("POST", "/_bulk");
      request.setJsonEntity(String.join("\n", bulkRequest) + "\n");
      httpResponse = client.performRequest(request);
    } catch (final ResponseException e) {
      throw new ElasticsearchExporterException("Elastic returned an error response on flush", e);
    } catch (final IOException e) {
      throw new ElasticsearchExporterException("Failed to flush bulk", e);
    }

    final BulkResponse bulkResponse;
    try {
      bulkResponse = MAPPER.readValue(httpResponse.getEntity().getContent(), BulkResponse.class);
    } catch (final IOException e) {
      throw new ElasticsearchExporterException("Failed to parse response when flushing", e);
    }

    if (bulkResponse.hasErrors()) {
      throwCollectedBulkError(bulkResponse);
    }
  }

  private void throwCollectedBulkError(final BulkResponse bulkResponse) {
    final var collectedErrors = new ArrayList<String>();
    bulkResponse.getItems().stream()
        .flatMap(item -> Optional.ofNullable(item.getIndex()).stream())
        .flatMap(index -> Optional.ofNullable(index.getError()).stream())
        .collect(Collectors.groupingBy(BulkItemError::getType))
        .forEach(
            (errorType, errors) ->
                collectedErrors.add(
                    String.format(
                        "Failed to flush %d item(s) of bulk request [type: %s, reason: %s]",
                        errors.size(), errorType, errors.get(0).getReason())));

    throw new ElasticsearchExporterException("Failed to flush bulk request: " + collectedErrors);
  }

  private int getBulkMemorySize() {
    return bulkRequest.stream().mapToInt(String::length).sum();
  }

  /**
   * @return true if request was acknowledged
   */
  private boolean putIndexTemplate(final String templateName, final Template template) {
    try {
      final var request = new Request("PUT", "/_index_template/" + templateName);
      request.setJsonEntity(MAPPER.writeValueAsString(template));

      final var response = client.performRequest(request);
      final var putIndexTemplateResponse =
          MAPPER.readValue(response.getEntity().getContent(), PutIndexTemplateResponse.class);
      return putIndexTemplateResponse.isAcknowledged();
    } catch (final IOException e) {
      throw new ElasticsearchExporterException("Failed to put index template", e);
    }
  }

  /**
   * @return true if request was acknowledged
   */
  private boolean putComponentTemplate(final String templateName, final Template template) {
    try {
      final var request = new Request("PUT", "/_component_template/" + templateName);
      request.setJsonEntity(MAPPER.writeValueAsString(template));

      final var response = client.performRequest(request);
      final var putIndexTemplateResponse =
          MAPPER.readValue(response.getEntity().getContent(), PutIndexTemplateResponse.class);
      return putIndexTemplateResponse.isAcknowledged();
    } catch (final IOException e) {
      throw new ElasticsearchExporterException("Failed to put component template", e);
    }
  }
}
