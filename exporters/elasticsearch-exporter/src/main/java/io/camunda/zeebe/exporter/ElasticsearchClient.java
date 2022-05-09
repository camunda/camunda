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
import io.camunda.zeebe.exporter.dto.BulkResponse;
import io.camunda.zeebe.exporter.dto.PutIndexTemplateResponse;
import io.camunda.zeebe.exporter.dto.Template;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.prometheus.client.Histogram;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;

public class ElasticsearchClient {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  protected final RestClient client;
  private final ElasticsearchExporterConfiguration configuration;
  private final TemplateReader templateReader;
  private final RecordIndexRouter indexRouter;

  private List<String> bulkRequest;
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

    bulk(newIndexCommand(record), record);
  }

  public void bulk(final Map<String, Object> command, final Record<?> record) {
    final String serializedCommand;

    try {
      serializedCommand = MAPPER.writeValueAsString(command);
    } catch (final IOException e) {
      throw new ElasticsearchExporterException(
          "Failed to serialize bulk request command to JSON", e);
    }

    final String jsonCommand = serializedCommand + "\n" + record.toJson();
    // don't re-append when retrying same record, to avoid OOM
    if (bulkRequest.isEmpty() || !bulkRequest.get(bulkRequest.size() - 1).equals(jsonCommand)) {
      bulkRequest.add(jsonCommand);
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
      bulkRequest = new ArrayList<>();
    } catch (final ElasticsearchExporterException e) {
      metrics.recordFailedFlush();
      throw e;
    }
  }

  private void exportBulk() {
    final Response httpResponse;
    try {
      httpResponse = sendBulkRequest();
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

  private Response sendBulkRequest() throws IOException {
    final var request = new Request("POST", "/_bulk");
    request.setJsonEntity(String.join("\n", bulkRequest) + "\n");

    return client.performRequest(request);
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

  private Map<String, Object> newIndexCommand(final Record<?> record) {
    final Map<String, Object> command = new HashMap<>();
    final Map<String, Object> contents = new HashMap<>();
    contents.put("_index", indexRouter.indexFor(record));
    contents.put("_id", indexRouter.idFor(record));
    contents.put("routing", indexRouter.routingFor(record));

    command.put("index", contents);
    return command;
  }
}
