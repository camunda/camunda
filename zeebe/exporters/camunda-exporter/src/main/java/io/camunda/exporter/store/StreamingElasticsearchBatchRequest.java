/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.store;

import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.json.JsonpMapper;
import co.elastic.clients.json.NdJsonpSerializable;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.exporter.errorhandling.Error;
import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.utils.ElasticsearchScriptBuilder;
import jakarta.json.stream.JsonGenerator;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.function.BiConsumer;
import org.apache.http.entity.EntityTemplate;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Executes bulk requests by streaming NDJSON directly to the HTTP socket via the low-level {@link
 * RestClient}, eliminating intermediate byte[] allocations.
 *
 * <p>This approach was originally used in the old Zeebe Elasticsearch exporter. See {@code
 * io.camunda.zeebe.exporter.ElasticsearchClient} and {@code
 * io.camunda.zeebe.exporter.BulkIndexRequest} in the {@code elasticsearch-exporter} module for the
 * reference implementation using {@link org.apache.http.entity.ContentProducer}.
 */
public final class StreamingElasticsearchBatchRequest extends ElasticsearchBatchRequest {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(StreamingElasticsearchBatchRequest.class);

  private final RestClient restClient;
  private final JsonpMapper jsonpMapper;
  private final ObjectMapper objectMapper;

  public StreamingElasticsearchBatchRequest(
      final RestClient restClient,
      final JsonpMapper jsonpMapper,
      final ObjectMapper objectMapper,
      final BulkRequest.Builder bulkRequestBuilder,
      final ElasticsearchScriptBuilder scriptBuilder) {
    super(null, bulkRequestBuilder, scriptBuilder);
    this.restClient = restClient;
    this.jsonpMapper = jsonpMapper;
    this.objectMapper = objectMapper;
  }

  @Override
  public void execute(final BiConsumer<String, Error> customErrorHandlers)
      throws PersistenceException {
    executeStreaming(customErrorHandlers, false);
  }

  @Override
  public void executeWithRefresh() throws PersistenceException {
    executeStreaming(null, true);
  }

  private void executeStreaming(
      final BiConsumer<String, Error> customErrorHandlers, final boolean shouldRefresh)
      throws PersistenceException {
    if (shouldRefresh) {
      bulkRequestBuilder.refresh(Refresh.True);
    }

    final var bulkRequest = bulkRequestBuilder.build();

    try {
      final EntityTemplate entity = new EntityTemplate(out -> writeNdJson(out, bulkRequest));
      entity.setContentType("application/x-ndjson");
      final Request request = new Request("POST", "/_bulk");
      request.setEntity(entity);
      final Response response = restClient.performRequest(request);

      // Buffer the response bytes so we can do a two-pass parse: first only the "errors" flag,
      // then the full response with all items only when there are actual errors.
      final byte[] responseBody;
      try (final var responseStream = response.getEntity().getContent()) {
        responseBody = responseStream.readAllBytes();
      }

      final var summary = objectMapper.readValue(responseBody, BulkErrorSummary.class);
      if (summary.errors()) {
        final var parser =
            jsonpMapper.jsonProvider().createParser(new ByteArrayInputStream(responseBody));
        final var bulkIndexResponse = BulkResponse._DESERIALIZER.deserialize(parser, jsonpMapper);
        validateNoErrors(bulkIndexResponse.items(), customErrorHandlers);
      }
      if (metrics != null) {
        metrics.recordBulkOperations(bulkRequest.operations().size());
      }
    } catch (final Exception ex) {
      throw new PersistenceException(
          "Error when processing bulk request against Elasticsearch: " + ex.getMessage(), ex);
    }
  }

  /**
   * Writes the request as NDJson lines. Taken from {@link
   * co.elastic.clients.transport.ElasticsearchTransportBase#collectNdJsonLines}
   */
  private void writeNdJson(final OutputStream out, final NdJsonpSerializable value)
      throws IOException {
    final Iterator<?> it = value._serializables();
    while (it.hasNext()) {
      final Object item = it.next();
      if (item == null) {
        continue;
      }
      if (item instanceof final NdJsonpSerializable nested && nested != value) {
        writeNdJson(out, nested);
      } else {
        final JsonGenerator generator = jsonpMapper.jsonProvider().createGenerator(out);
        jsonpMapper.serialize(item, generator);
        generator.flush();
        out.write('\n');
      }
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record BulkErrorSummary(boolean errors) {}
}
