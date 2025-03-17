/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util.j5templates;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.config.operate.OperateElasticsearchProperties;
import io.camunda.config.operate.OperateProperties;
import io.camunda.operate.schema.SchemaManager;
import io.camunda.operate.schema.util.camunda.exporter.SchemaWithExporter;
import io.camunda.operate.util.IndexPrefixHolder;
import io.camunda.operate.util.TestUtil;
import java.io.IOException;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.client.indices.GetIndexResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class ElasticsearchContainerManager extends SearchContainerManager {

  protected static final Logger LOGGER =
      LoggerFactory.getLogger(ElasticsearchContainerManager.class);

  private static final String OPEN_SCROLL_CONTEXT_FIELD = "open_contexts";
  // Path to find search statistics for all indexes
  private static final String PATH_SEARCH_STATISTICS =
      "/_nodes/stats/indices/search?filter_path=nodes.*.indices.search";

  protected final RestHighLevelClient esClient;

  private final IndexPrefixHolder indexPrefixHolder;

  public ElasticsearchContainerManager(
      @Qualifier("esClient") final RestHighLevelClient esClient,
      final OperateProperties operateProperties,
      final SchemaManager schemaManager,
      final IndexPrefixHolder indexPrefixHolder) {
    super(operateProperties, schemaManager);
    this.esClient = esClient;
    this.indexPrefixHolder = indexPrefixHolder;
  }

  @Override
  public void startContainer() {
    if (indexPrefix == null) {
      indexPrefix = indexPrefixHolder.createNewIndexPrefix();
    }
    updatePropertiesIndexPrefix();
    if (shouldCreateSchema()) {
      final var schemaExporterHelper = new SchemaWithExporter(indexPrefix, true);
      schemaExporterHelper.createSchema();
      assertThat(areIndicesCreatedAfterChecks(indexPrefix, 19, 5 * 60 /*sec*/))
          .describedAs("Search %s (min %d) indices are created", indexPrefix, 5)
          .isTrue();
    }
  }

  @Override
  protected void updatePropertiesIndexPrefix() {
    operateProperties.getElasticsearch().setIndexPrefix(indexPrefix);
  }

  @Override
  protected boolean shouldCreateSchema() {
    return operateProperties.getElasticsearch().isCreateSchema();
  }

  @Override
  protected boolean areIndicesCreated(final String indexPrefix, final int minCountOfIndices)
      throws IOException {
    final GetIndexResponse response =
        esClient
            .indices()
            .get(
                new GetIndexRequest(indexPrefix + "*")
                    .indicesOptions(IndicesOptions.fromOptions(true, false, true, false)),
                RequestOptions.DEFAULT);
    final String[] indices = response.getIndices();
    return indices != null && indices.length >= minCountOfIndices;
  }

  @Override
  public void stopContainer() {
    // TestUtil.removeIlmPolicy(esClient);
    final String indexPrefix = operateProperties.getElasticsearch().getIndexPrefix();
    TestUtil.removeAllIndices(esClient, indexPrefix);
    operateProperties
        .getElasticsearch()
        .setIndexPrefix(OperateElasticsearchProperties.DEFAULT_INDEX_PREFIX);

    assertThat(getIntValueForJSON(PATH_SEARCH_STATISTICS, OPEN_SCROLL_CONTEXT_FIELD, 0))
        .describedAs("There are too many open scroll contexts left.")
        .isLessThanOrEqualTo(15);
  }

  private int getIntValueForJSON(
      final String path, final String fieldname, final int defaultValue) {
    final ObjectMapper objectMapper = new ObjectMapper();
    try {
      final Response response =
          esClient.getLowLevelClient().performRequest(new Request("GET", path));
      final JsonNode jsonNode = objectMapper.readTree(response.getEntity().getContent());
      final JsonNode field = jsonNode.findValue(fieldname);
      if (field != null) {
        return field.asInt(defaultValue);
      }
    } catch (final Exception e) {
      LOGGER.error("Couldn't retrieve json object from elasticsearch. Return Optional.Empty.", e);
    }

    return defaultValue;
  }
}
