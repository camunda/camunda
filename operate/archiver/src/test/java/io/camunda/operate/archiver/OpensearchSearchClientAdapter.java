/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.archiver;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.opensearch.ExtendedOpenSearchClient;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.SchemaManager;
import io.camunda.operate.schema.indices.IndexDescriptor;
import io.camunda.operate.schema.opensearch.OpensearchSchemaManager;
import io.camunda.operate.schema.templates.TemplateDescriptor;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.hc.core5.http.HttpHost;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.opensearch.testcontainers.OpenSearchContainer;

class OpensearchSearchClientAdapter
    implements ArchiverJobIT.SearchClientAdapter<ExtendedOpenSearchClient> {

  private final ObjectMapper objectMapper;
  private final OpenSearchClient client;
  private final ExtendedOpenSearchClient extendedClient;

  OpensearchSearchClientAdapter(
      final OpenSearchContainer<?> container, final ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
    final var host = new HttpHost("http", container.getHost(), container.getMappedPort(9200));
    final var transport =
        ApacheHttpClient5TransportBuilder.builder(host)
            .setMapper(new JacksonJsonpMapper(objectMapper))
            .build();
    client = new OpenSearchClient(transport);
    extendedClient = new ExtendedOpenSearchClient(transport);
  }

  @Override
  public ExtendedOpenSearchClient getClient() {
    return extendedClient;
  }

  @Override
  public void index(
      final String id, final String routing, final String indexName, final Object entity)
      throws IOException {
    client.index(
        r -> {
          final var req = r.index(indexName).id(id).document(entity).refresh(Refresh.True);
          if (routing != null) {
            req.routing(routing);
          }
          return req;
        });
  }

  @Override
  public boolean exists(final String id, final String routing, final String indexName)
      throws IOException {
    final var resp =
        client.get(
            r -> {
              final var req = r.index(indexName).id(id);
              if (routing != null) {
                req.routing(routing);
              }
              return req;
            },
            Map.class);
    return resp.found();
  }

  @Override
  public void refresh() throws IOException {
    client.indices().refresh(r -> r);
  }

  @Override
  public void deleteIndices(final String pattern) throws IOException {
    try {
      client.indices().delete(r -> r.index(List.of(pattern)).ignoreUnavailable(true));
    } catch (final Exception ignored) {
      // no matching indices is fine
    }
  }

  SchemaManager buildSchemaManager(
      final OperateProperties props,
      final List<TemplateDescriptor> templates,
      final List<IndexDescriptor> indices) {
    final var richClient =
        new RichOpenSearchClient(
            null, client, new OpenSearchAsyncClient(client._transport()), objectMapper);
    return new OpensearchSchemaManager(
        props,
        richClient,
        new ArrayList<>(templates),
        new ArrayList<IndexDescriptor>(indices),
        objectMapper);
  }
}
