/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util.j5templates;

import static io.camunda.operate.property.OperateElasticsearchProperties.DEFAULT_INDEX_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ExpandWildcard;
import co.elastic.clients.elasticsearch.indices.GetIndexRequest;
import co.elastic.clients.elasticsearch.nodes.Stats;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.qa.util.TestSchemaManager;
import io.camunda.operate.schema.util.camunda.exporter.SchemaWithExporter;
import io.camunda.operate.util.IndexPrefixHolder;
import io.camunda.operate.util.TestUtil;
import io.camunda.search.schema.config.SearchEngineConfiguration;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class ElasticsearchContainerManager extends SearchContainerManager {

  protected static final Logger LOGGER =
      LoggerFactory.getLogger(ElasticsearchContainerManager.class);

  protected final ElasticsearchClient es8Client;

  private final IndexPrefixHolder indexPrefixHolder;

  public ElasticsearchContainerManager(
      final ElasticsearchClient es8Client,
      final SearchEngineConfiguration searchEngineConfiguration,
      final OperateProperties operateProperties,
      final TestSchemaManager schemaManager,
      final IndexPrefixHolder indexPrefixHolder) {
    super(searchEngineConfiguration, operateProperties, schemaManager);
    this.es8Client = es8Client;
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
    searchEngineConfiguration.connect().setIndexPrefix(indexPrefix);
    operateProperties.getElasticsearch().setIndexPrefix(indexPrefix);
  }

  @Override
  protected boolean shouldCreateSchema() {
    return operateProperties.getElasticsearch().isCreateSchema();
  }

  @Override
  protected boolean areIndicesCreated(final String indexPrefix, final int minCountOfIndices)
      throws IOException {
    final var getIndexRequest =
        new GetIndexRequest.Builder()
            .index(indexPrefix + "*")
            .ignoreUnavailable(true)
            .allowNoIndices(false)
            .expandWildcards(ExpandWildcard.Open)
            .build();
    final var response = es8Client.indices().get(getIndexRequest);
    return response.result() != null && response.result().size() >= minCountOfIndices;
  }

  @Override
  public void stopContainer() {
    // TestUtil.removeIlmPolicy(esClient);
    final String indexPrefix = searchEngineConfiguration.connect().getIndexPrefix();
    TestUtil.removeAllIndices(es8Client, indexPrefix);
    searchEngineConfiguration.connect().setIndexPrefix(DEFAULT_INDEX_PREFIX);
    operateProperties.getElasticsearch().setIndexPrefix(DEFAULT_INDEX_PREFIX);

    assertThat(getOpenScrollContextCount())
        .describedAs("There are too many open scroll contexts left.")
        .isLessThanOrEqualTo(15);
  }

  private int getOpenScrollContextCount() {
    int openContexts = 0;
    try {
      final Set<Map.Entry<String, Stats>> nodesResult =
          es8Client.nodes().stats().nodes().entrySet();
      for (final Map.Entry<String, Stats> entryNodes : nodesResult) {
        openContexts += entryNodes.getValue().indices().search().openContexts().intValue();
      }
      return openContexts;
    } catch (final IOException e) {
      LOGGER.error("Couldn't retrieve node stats from elasticsearch.", e);
      return 0;
    }
  }
}
