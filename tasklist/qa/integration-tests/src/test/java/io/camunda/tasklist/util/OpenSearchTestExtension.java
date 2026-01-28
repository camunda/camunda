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

import io.camunda.search.schema.config.SearchEngineConfiguration;
import io.camunda.tasklist.CommonUtils;
import io.camunda.tasklist.property.TasklistOpenSearchProperties;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.qa.util.TasklistIndexPrefixHolder;
import io.camunda.tasklist.qa.util.TestSchemaManager;
import io.camunda.tasklist.qa.util.TestUtil;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.entities.ExporterEntity;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.IndexOperation;
import org.opensearch.client.opensearch.indices.FlushRequest;
import org.opensearch.client.opensearch.nodes.Stats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(SCOPE_PROTOTYPE)
@ConditionalOnProperty(name = "camunda.data.secondary-storage.type", havingValue = "opensearch")
public class OpenSearchTestExtension
    implements DatabaseTestExtension,
        BeforeEachCallback,
        AfterEachCallback,
        TestExecutionExceptionHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(OpenSearchTestExtension.class);

  @Autowired
  @Qualifier("tasklistOsClient")
  private OpenSearchClient osClient;

  @Autowired private TasklistProperties tasklistProperties;
  @Autowired private SearchEngineConfiguration searchEngineConfiguration;
  private boolean failed = false;
  @Autowired private TestSchemaManager schemaManager;

  @Autowired private TasklistIndexPrefixHolder indexPrefixHolder;
  private String indexPrefix;

  @Override
  public void beforeEach(final ExtensionContext extensionContext) {
    indexPrefix = tasklistProperties.getOpenSearch().getIndexPrefix();
    if (indexPrefix.isBlank()) {
      indexPrefix =
          Optional.ofNullable(indexPrefixHolder.createNewIndexPrefix()).orElse(indexPrefix);
      tasklistProperties.getOpenSearch().setIndexPrefix(indexPrefix);
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
          prefix -> TestUtil.removeAllIndices(osClient, prefix));
    }
    tasklistProperties
        .getOpenSearch()
        .setIndexPrefix(TasklistOpenSearchProperties.DEFAULT_INDEX_PREFIX);
    searchEngineConfiguration
        .connect()
        .setIndexPrefix(TasklistOpenSearchProperties.DEFAULT_INDEX_PREFIX);
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

      final FlushRequest flush =
          FlushRequest.of(
              builder ->
                  builder
                      .force(true)
                      .index(tasklistProperties.getOpenSearch().getIndexPrefix() + "*"));
      osClient.indices().flush(flush);
    } catch (final Exception t) {
      LOGGER.error("Could not refresh Tasklist Opensearch indices", t);
    }
  }

  @Override
  public int getOpenScrollcontextSize() {
    int openContext = 0;
    try {
      final Set<Map.Entry<String, Stats>> nodesResult = osClient.nodes().stats().nodes().entrySet();
      for (final Map.Entry<String, Stats> entryNodes : nodesResult) {
        openContext += entryNodes.getValue().indices().search().openContexts().intValue();
      }
      return openContext;
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public <T extends ExporterEntity> void bulkIndex(
      final IndexDescriptor index,
      final List<T> documents,
      final Function<T, String> routingFunction)
      throws IOException {
    osClient.bulk(
        b ->
            b.refresh(Refresh.True)
                .operations(
                    documents.stream()
                        .map(
                            document ->
                                new BulkOperation.Builder()
                                    .index(
                                        IndexOperation.of(
                                            i ->
                                                i.index(index.getFullQualifiedName())
                                                    .id((document.getId()))
                                                    .routing(routingFunction.apply(document))
                                                    .document(
                                                        CommonUtils.getJsonObjectFromEntity(
                                                            document))))
                                    .build())
                        .toList()));
  }
}
