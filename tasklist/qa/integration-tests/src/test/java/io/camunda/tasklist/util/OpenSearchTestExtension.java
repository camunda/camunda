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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.exporter.schema.config.SearchEngineConfiguration;
import io.camunda.tasklist.property.TasklistOpenSearchProperties;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.qa.util.TasklistIndexPrefixHolder;
import io.camunda.tasklist.qa.util.TestUtil;
import io.camunda.tasklist.schema.manager.SchemaManager;
import io.camunda.tasklist.zeebe.ImportValueType;
import io.camunda.tasklist.zeebeimport.RecordsReader;
import io.camunda.tasklist.zeebeimport.RecordsReaderHolder;
import io.camunda.tasklist.zeebeimport.ZeebeImporter;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch.core.DeleteByQueryRequest;
import org.opensearch.client.opensearch.indices.FlushRequest;
import org.opensearch.client.opensearch.indices.GetIndexResponse;
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
@ConditionalOnProperty(name = "camunda.tasklist.database", havingValue = "opensearch")
public class OpenSearchTestExtension
    implements DatabaseTestExtension,
        BeforeEachCallback,
        AfterEachCallback,
        TestExecutionExceptionHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(OpenSearchTestExtension.class);

  /** Scroll contexts constants */
  private static final String OPEN_SCROLL_CONTEXT_FIELD = "open_contexts";

  /** Path to find search statistics for all indexes */
  private static final String PATH_SEARCH_STATISTICS =
      "/_nodes/stats/indices/search?filter_path=nodes.*.indices.search";

  @Autowired
  @Qualifier("tasklistOsClient")
  private OpenSearchClient osClient;

  @Autowired
  @Qualifier("tasklistZeebeOsClient")
  private OpenSearchClient zeebeOsClient;

  @Autowired private TasklistProperties tasklistProperties;
  @Autowired private SearchEngineConfiguration searchEngineConfiguration;
  @Autowired private ZeebeImporter zeebeImporter;
  @Autowired private RecordsReaderHolder recordsReaderHolder;
  private boolean failed = false;
  @Autowired private SchemaManager schemaManager;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private TestImportListener testImportListener;
  @Autowired private TasklistIndexPrefixHolder indexPrefixHolder;
  private String indexPrefix;

  @Override
  public void beforeEach(final ExtensionContext extensionContext) {
    indexPrefix = tasklistProperties.getOpenSearch().getIndexPrefix();
    if (indexPrefix.isBlank()) {
      indexPrefix =
          Optional.ofNullable(indexPrefixHolder.createNewIndexPrefix()).orElse(indexPrefix);
      tasklistProperties.getOpenSearch().setIndexPrefix(indexPrefix);
      tasklistProperties.getZeebeOpenSearch().setPrefix(indexPrefix);
      searchEngineConfiguration.connect().setIndexPrefix(indexPrefix);
    }
    /* Needed for the tasklist-user index */
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
      final String indexPrefix = tasklistProperties.getOpenSearch().getIndexPrefix();
      TestUtil.removeAllIndices(osClient, indexPrefix);
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
  public void refreshIndexesInElasticsearch() {
    refreshZeebeIndices();
    refreshTasklistIndices();
  }

  @Override
  public void refreshZeebeIndices() {
    try {
      zeebeOsClient
          .indices()
          .refresh(
              r -> r.index(List.of(tasklistProperties.getZeebeOpenSearch().getPrefix() + "*")));
    } catch (final Exception t) {
      LOGGER.error("Could not refresh Zeebe OpenSearch indices", t);
    }
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
  public boolean areIndicesCreatedAfterChecks(
      final String indexPrefix, final int minCountOfIndices, final int maxChecks) {
    boolean areCreated = false;
    int checks = 0;
    while (!areCreated && checks <= maxChecks) {
      checks++;
      try {
        areCreated = areIndicesAreCreated(indexPrefix, minCountOfIndices);
      } catch (final Exception t) {
        LOGGER.error(
            "OpenSearch {} indices (min {}) are not created yet. Waiting {}/{}",
            indexPrefix,
            minCountOfIndices,
            checks,
            maxChecks);
        sleepFor(200);
      }
    }
    LOGGER.debug("OpenSearch indices are created after {} checks", checks);
    return areCreated;
  }

  @Override
  public List<RecordsReader> getRecordsReaders(final ImportValueType importValueType) {
    return recordsReaderHolder.getAllRecordsReaders().stream()
        .filter(rr -> rr.getImportValueType().equals(importValueType))
        .collect(Collectors.toList());
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
  public <T> long deleteByTermsQuery(
      final String index,
      final String fieldName,
      final Collection<T> values,
      final Class<T> valueType)
      throws IOException {
    final Function<? super T, ? extends FieldValue> valueMapper;
    if (valueType == String.class) {
      valueMapper = v -> FieldValue.of((String) v);
    } else if (valueType == Long.class) {
      valueMapper = v -> FieldValue.of((Long) v);
    } else {
      throw new UnsupportedOperationException(
          "Unsupported valueType: " + valueType + ". Please implement it.");
    }
    return zeebeOsClient
        .deleteByQuery(
            new DeleteByQueryRequest.Builder()
                .index(index)
                .waitForCompletion(true)
                .query(
                    q ->
                        q.terms(
                            term ->
                                term.field(fieldName)
                                    .terms(
                                        terms ->
                                            terms.value(
                                                values.stream()
                                                    .map(valueMapper)
                                                    .collect(Collectors.toList())))))
                .build())
        .deleted();
  }

  private boolean areIndicesAreCreated(final String indexPrefix, final int minCountOfIndices)
      throws IOException {
    final GetIndexResponse response =
        osClient
            .indices()
            .get(
                g ->
                    g.index(List.of(indexPrefix + "*"))
                        .ignoreUnavailable(true)
                        .allowNoIndices(false));

    final Set<String> indices = response.result().keySet();
    return indices != null && indices.size() >= minCountOfIndices;
  }
}
