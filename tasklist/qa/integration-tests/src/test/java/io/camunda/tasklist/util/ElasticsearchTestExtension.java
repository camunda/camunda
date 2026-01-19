/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.IndexOperation;
import co.elastic.clients.elasticsearch.indices.FlushRequest;
import co.elastic.clients.elasticsearch.nodes.Stats;
import io.camunda.search.schema.config.SearchEngineConfiguration;
import io.camunda.tasklist.property.TasklistElasticsearchProperties;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.qa.util.TasklistIndexPrefixHolder;
import io.camunda.tasklist.qa.util.TestSchemaManager;
import io.camunda.tasklist.qa.util.TestUtil;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.entities.ExporterEntity;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(SCOPE_PROTOTYPE)
@ConditionalOnProperty(
    name = "camunda.data.secondary-storage.type",
    havingValue = "elasticsearch",
    matchIfMissing = true)
public class ElasticsearchTestExtension
    implements DatabaseTestExtension,
        BeforeEachCallback,
        AfterEachCallback,
        TestExecutionExceptionHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchTestExtension.class);

  @Autowired
  @Qualifier("tasklistEsClient")
  private ElasticsearchClient esClient;

  @Autowired private TasklistProperties tasklistProperties;
  @Autowired private SearchEngineConfiguration searchEngineConfiguration;
  private boolean failed = false;
  @Autowired private TestSchemaManager schemaManager;
  @Autowired private TasklistIndexPrefixHolder indexPrefixHolder;
  private String indexPrefix;

  @Override
  public void beforeEach(final ExtensionContext extensionContext) {
    indexPrefix = tasklistProperties.getElasticsearch().getIndexPrefix();
    if (indexPrefix == null || indexPrefix.isBlank()) {
      indexPrefix = indexPrefixHolder.createNewIndexPrefix();
      tasklistProperties.getElasticsearch().setIndexPrefix(indexPrefix);
      searchEngineConfiguration.connect().setIndexPrefix(indexPrefix);
    }
    schemaManager.createSchema();
  }

  @Override
  public void handleTestExecutionException(
      final ExtensionContext context, final Throwable throwable) throws Throwable {
    failed = true;
    throw throwable;
  }

  @Override
  public void afterEach(final ExtensionContext extensionContext) {
    if (!failed) {
      indexPrefixHolder.cleanupIndicesIfNeeded(
          prefix -> TestUtil.removeAllIndices(esClient, prefix));
    }
    tasklistProperties
        .getElasticsearch()
        .setIndexPrefix(TasklistElasticsearchProperties.DEFAULT_INDEX_PREFIX);
    searchEngineConfiguration
        .connect()
        .setIndexPrefix(TasklistElasticsearchProperties.DEFAULT_INDEX_PREFIX);
    assertMaxOpenScrollContexts(10);
  }

  @Override
  public void assertMaxOpenScrollContexts(final int maxOpenScrollContexts) {
    assertThat(getOpenScrollcontextSize())
        .describedAs("There are too many open scroll contexts left.")
        .isLessThanOrEqualTo(maxOpenScrollContexts);
  }

  @Override
  public void refreshTasklistIndices() {
    try {
      final FlushRequest flushRequest =
          FlushRequest.of(
              f ->
                  f.index(tasklistProperties.getElasticsearch().getIndexPrefix() + "*")
                      .force(true));
      esClient.indices().flush(flushRequest);
    } catch (final Exception t) {
      LOGGER.error("Could not refresh Tasklist Elasticsearch indices", t);
    }
  }

  @Override
  public int getOpenScrollcontextSize() {
    int openContexts = 0;
    try {
      final Set<Map.Entry<String, Stats>> nodesResult = esClient.nodes().stats().nodes().entrySet();
      for (final Map.Entry<String, Stats> nodeEntry : nodesResult) {
        openContexts += nodeEntry.getValue().indices().search().openContexts().intValue();
      }
      return openContexts;
    } catch (final IOException e) {
      LOGGER.error("Couldn't retrieve node stats from elasticsearch.", e);
      return 0;
    }
  }

  @Override
  public <T extends ExporterEntity> void bulkIndex(
      final IndexDescriptor index,
      final List<T> documents,
      final Function<T, String> routingFunction)
      throws IOException {
    final List<BulkOperation> operations =
        documents.stream()
            .map(
                document ->
                    BulkOperation.of(
                        op ->
                            op.index(
                                IndexOperation.of(
                                    idx ->
                                        idx.index(index.getFullQualifiedName())
                                            .id(document.getId())
                                            .routing(routingFunction.apply(document))
                                            .document(document)))))
            .toList();

    final BulkRequest bulkRequest =
        BulkRequest.of(b -> b.operations(operations).refresh(Refresh.True));
    esClient.bulk(bulkRequest);
  }
}
