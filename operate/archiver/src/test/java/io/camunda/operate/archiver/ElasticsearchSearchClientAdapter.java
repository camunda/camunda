/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.archiver;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.SchemaManager;
import io.camunda.operate.schema.elasticsearch.ElasticsearchSchemaManager;
import io.camunda.operate.schema.indices.IndexDescriptor;
import io.camunda.operate.schema.templates.TemplateDescriptor;
import io.camunda.operate.store.elasticsearch.RetryElasticsearchClient;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.http.HttpHost;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.action.support.WriteRequest.RefreshPolicy;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.RestHighLevelClientBuilder;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.xcontent.XContentType;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

class ElasticsearchSearchClientAdapter
    implements ArchiverJobIT.SearchClientAdapter<RestHighLevelClient> {

  private final ObjectMapper objectMapper;
  private final RestHighLevelClient client;

  ElasticsearchSearchClientAdapter(
      final ElasticsearchContainer container, final ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
    client =
        new RestHighLevelClientBuilder(
                RestClient.builder(
                        new HttpHost(container.getHost(), container.getMappedPort(9200), "http"))
                    .build())
            .setApiCompatibilityMode(true)
            .build();
  }

  @Override
  public RestHighLevelClient getClient() {
    return client;
  }

  @Override
  public void index(
      final String id, final String routing, final String indexName, final Object entity)
      throws IOException {
    final var req =
        new IndexRequest(indexName)
            .id(id)
            .source(objectMapper.writeValueAsBytes(entity), XContentType.JSON)
            .setRefreshPolicy(RefreshPolicy.IMMEDIATE);
    if (routing != null) {
      req.routing(routing);
    }
    client.index(req, RequestOptions.DEFAULT);
  }

  @Override
  public boolean exists(final String id, final String routing, final String indexName)
      throws IOException {
    final var req = new GetRequest(indexName, id);
    if (routing != null) {
      req.routing(routing);
    }
    return client.get(req, RequestOptions.DEFAULT).isExists();
  }

  @Override
  public void refresh() throws IOException {
    client.indices().refresh(new RefreshRequest(), RequestOptions.DEFAULT);
  }

  @Override
  public void deleteIndices(final String pattern) throws IOException {
    final var getResp =
        client
            .indices()
            .get(
                new GetIndexRequest(pattern).indicesOptions(IndicesOptions.lenientExpandOpen()),
                RequestOptions.DEFAULT);
    final String[] indices = getResp.getIndices();
    if (indices != null && indices.length > 0) {
      client
          .indices()
          .delete(
              new DeleteIndexRequest(indices).indicesOptions(IndicesOptions.lenientExpandOpen()),
              RequestOptions.DEFAULT);
    }
  }

  SchemaManager buildSchemaManager(
      final OperateProperties props,
      final List<TemplateDescriptor> templates,
      final List<IndexDescriptor> indices) {
    final var retryClient = new RetryElasticsearchClient();
    ReflectionTestUtils.setField(retryClient, "esClient", client);
    final var manager = new ElasticsearchSchemaManager();
    ReflectionTestUtils.setField(manager, "retryElasticsearchClient", retryClient);
    ReflectionTestUtils.setField(manager, "operateProperties", props);
    ReflectionTestUtils.setField(manager, "templateDescriptors", new ArrayList<>(templates));
    ReflectionTestUtils.setField(manager, "indexDescriptors", new ArrayList<>(indices));
    ReflectionTestUtils.setField(manager, "objectMapper", objectMapper);
    return manager;
  }
}
