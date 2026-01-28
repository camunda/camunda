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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Optional;
import java.util.function.Function;
import org.elasticsearch.action.admin.indices.flush.FlushRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.support.WriteRequest.RefreshPolicy;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.xcontent.XContentType;
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

  /** Scroll contexts constants */
  private static final String OPEN_SCROLL_CONTEXT_FIELD = "open_contexts";

  /** Path to find search statistics for all indexes */
  private static final String PATH_SEARCH_STATISTICS =
      "/_nodes/stats/indices/search?filter_path=nodes.*.indices.search";

  @Autowired
  @Qualifier("tasklistEsClient")
  private RestHighLevelClient esClient;

  @Autowired private TasklistProperties tasklistProperties;
  @Autowired private SearchEngineConfiguration searchEngineConfiguration;
  private boolean failed = false;
  @Autowired private TestSchemaManager schemaManager;
  @Autowired private TasklistIndexPrefixHolder indexPrefixHolder;
  private String indexPrefix;
  @Autowired private ObjectMapper objectMapper;

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

      final FlushRequest flush =
          new FlushRequest(tasklistProperties.getElasticsearch().getIndexPrefix() + "*");
      esClient
          .indices()
          .flush(flush, RequestOptions.DEFAULT.toBuilder().addParameter("force", "true").build());
    } catch (final Exception t) {
      LOGGER.error("Could not refresh Tasklist Elasticsearch indices", t);
    }
  }

  @Override
  public int getOpenScrollcontextSize() {
    return getIntValueForJSON(PATH_SEARCH_STATISTICS, OPEN_SCROLL_CONTEXT_FIELD, 0);
  }

  @Override
  public <T extends ExporterEntity> void bulkIndex(
      final IndexDescriptor index,
      final List<T> documents,
      final Function<T, String> routingFunction)
      throws IOException {
    final var bulkRequest = new BulkRequest().setRefreshPolicy(RefreshPolicy.IMMEDIATE);
    for (final var document : documents) {
      bulkRequest.add(
          new IndexRequest()
              .index(index.getFullQualifiedName())
              .id(document.getId())
              .routing(routingFunction.apply(document))
              .source(objectMapper.writeValueAsString(document), XContentType.JSON));
    }
    esClient.bulk(bulkRequest, RequestOptions.DEFAULT);
  }

  private int getIntValueForJSON(
      final String path, final String fieldname, final int defaultValue) {
    final Optional<JsonNode> jsonNode = getJsonFor(path);
    if (jsonNode.isPresent()) {
      final JsonNode field = jsonNode.get().findValue(fieldname);
      if (field != null) {
        return field.asInt(defaultValue);
      }
    }
    return defaultValue;
  }

  private Optional<JsonNode> getJsonFor(final String path) {
    try {
      final ObjectMapper objectMapper = new ObjectMapper();
      final Response response =
          esClient.getLowLevelClient().performRequest(new Request("GET", path));
      return Optional.of(objectMapper.readTree(response.getEntity().getContent()));
    } catch (final Exception e) {
      LOGGER.error("Couldn't retrieve json object from elasticsearch. Return Optional.Empty.", e);
      return Optional.empty();
    }
  }
}
