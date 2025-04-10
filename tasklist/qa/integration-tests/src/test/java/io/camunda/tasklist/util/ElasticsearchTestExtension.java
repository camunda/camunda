/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.util;

import static io.camunda.tasklist.util.ThreadUtil.sleepFor;
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
import java.util.Optional;
import java.util.function.Supplier;
import org.elasticsearch.action.admin.indices.flush.FlushRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestHighLevelClient;
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
    name = "camunda.tasklist.database",
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

  @Autowired
  @Qualifier("tasklistZeebeEsClient")
  private RestHighLevelClient zeebeEsClient;

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
      indexPrefix =
          Optional.ofNullable(indexPrefixHolder.createNewIndexPrefix()).orElse(indexPrefix);
      tasklistProperties.getElasticsearch().setIndexPrefix(indexPrefix);
      tasklistProperties.getZeebeElasticsearch().setPrefix(indexPrefix);
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
      final String indexPrefix = tasklistProperties.getElasticsearch().getIndexPrefix();
      TestUtil.removeAllIndices(esClient, indexPrefix);
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
  public void refreshIndexesInElasticsearch() {
    refreshZeebeIndices();
    refreshTasklistIndices();
  }

  @Override
  public void refreshZeebeIndices() {
    try {
      final RefreshRequest refreshRequest =
          new RefreshRequest(tasklistProperties.getZeebeElasticsearch().getPrefix() + "*");
      zeebeEsClient.indices().refresh(refreshRequest, RequestOptions.DEFAULT);
    } catch (final Exception t) {
      LOGGER.error("Could not refresh Zeebe Elasticsearch indices", t);
    }
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
  public void processAllRecordsAndWait(final TestCheck testCheck, final Object... arguments) {
    processRecordsAndWaitFor(testCheck, null, arguments);
  }

  @Override
  public void processRecordsAndWaitFor(
      final TestCheck testCheck, final Supplier<Object> supplier, final Object... arguments) {
    int waitingRound = 0;
    final int maxRounds = 50;
    boolean found = testCheck.test(arguments);
    final long start = System.currentTimeMillis();
    while (!found && waitingRound < maxRounds) {
      try {
        if (supplier != null) {
          supplier.get();
        }
        refreshIndexesInElasticsearch();
      } catch (final Exception e) {
        LOGGER.error(e.getMessage(), e);
      }
      found = testCheck.test(arguments);
      if (!found) {
        sleepFor(500);
        waitingRound++;
      }
    }
    final long finishedTime = System.currentTimeMillis() - start;

    if (found) {
      LOGGER.debug(
          "Condition {} was met in round {} ({} ms).",
          testCheck.getName(),
          waitingRound,
          finishedTime);
    } else {
      LOGGER.error(
          "Condition {} was not met after {} rounds ({} ms).",
          testCheck.getName(),
          waitingRound,
          finishedTime);
    }
  }

  @Override
  public int getOpenScrollcontextSize() {
    return getIntValueForJSON(PATH_SEARCH_STATISTICS, OPEN_SCROLL_CONTEXT_FIELD, 0);
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
