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
import io.camunda.zeebe.exporter.dto.BulkItemError;
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

public class ElasticsearchClient {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final RestClient client;
  private final ElasticsearchExporterConfiguration configuration;
  private final TemplateReader templateReader;
  private final RecordIndexRouter indexRouter;
  private final List<String> bulkRequest;

  private ElasticsearchMetrics metrics;

  public ElasticsearchClient(final ElasticsearchExporterConfiguration configuration) {
    this(configuration, new ArrayList<>());
  }

  ElasticsearchClient(
      final ElasticsearchExporterConfiguration configuration, final List<String> bulkRequest) {
    this.configuration = configuration;
    this.bulkRequest = bulkRequest;

    templateReader = new TemplateReader(configuration.index);
    indexRouter = new RecordIndexRouter(configuration.index);
    client = RestClientFactory.of(configuration);
  }

  public void close() throws IOException {
    client.close();
  }

  public void index(final Record<?> record) {
    if (metrics == null) {
      metrics = new ElasticsearchMetrics(record.getPartitionId());
    }

    final BulkIndexAction action =
        new BulkIndexAction(
            indexRouter.indexFor(record),
            indexRouter.idFor(record),
            indexRouter.routingFor(record));
    bulk(action, record);
  }

  public void bulk(final BulkIndexAction action, final Record<?> record) {
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

  public boolean shouldFlush() {
    return bulkRequest.size() >= configuration.bulk.size
        || getBulkMemorySize() >= configuration.bulk.memoryLimit;
  }

  private int getBulkMemorySize() {
    return bulkRequest.stream().mapToInt(String::length).sum();
  }

  /**
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
   * Creates or updates the component template on the target Elasticsearch. The template is read
   * from {@link TemplateReader#readComponentTemplate()}.
   */
  public boolean putComponentTemplate() {
    final Template template = templateReader.readComponentTemplate();
    return putComponentTemplate(template);
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
      return putIndexTemplateResponse.acknowledged();
    } catch (final IOException e) {
      throw new ElasticsearchExporterException("Failed to put index template", e);
    }
  }

  /**
   * @return true if request was acknowledged
   */
  private boolean putComponentTemplate(final Template template) {
    try {
      final var request = new Request("PUT", "/_component_template/" + configuration.index.prefix);
      request.setJsonEntity(MAPPER.writeValueAsString(template));

      final var response = client.performRequest(request);
      final var putIndexTemplateResponse =
          MAPPER.readValue(response.getEntity().getContent(), PutIndexTemplateResponse.class);
      return putIndexTemplateResponse.acknowledged();
    } catch (final IOException e) {
      throw new ElasticsearchExporterException("Failed to put component template", e);
    }
  }
}
