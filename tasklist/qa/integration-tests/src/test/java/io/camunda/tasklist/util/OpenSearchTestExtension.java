/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.util;

import static io.camunda.tasklist.util.ThreadUtil.sleepFor;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.property.TasklistOpenSearchProperties;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.manager.SchemaManager;
import io.camunda.tasklist.zeebe.ImportValueType;
import io.camunda.tasklist.zeebeimport.RecordsReader;
import io.camunda.tasklist.zeebeimport.RecordsReaderHolder;
import io.camunda.tasklist.zeebeimport.ZeebeImporter;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.indices.GetIndexResponse;
import org.opensearch.client.opensearch.nodes.Stats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
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
  @Qualifier("openSearchClient")
  protected OpenSearchClient osClient;

  @Autowired
  @Qualifier("zeebeOsClient")
  protected OpenSearchClient zeebeOsClient;

  @Autowired protected TasklistProperties tasklistProperties;
  @Autowired protected ZeebeImporter zeebeImporter;
  @Autowired protected RecordsReaderHolder recordsReaderHolder;
  protected boolean failed = false;
  @Autowired private SchemaManager schemaManager;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private TestImportListener testImportListener;
  private String indexPrefix;

  @Override
  public void beforeEach(ExtensionContext extensionContext) {
    if (indexPrefix == null) {
      indexPrefix = TestUtil.createRandomString(10) + "-tasklist";
    }
    tasklistProperties.getOpenSearch().setIndexPrefix(indexPrefix);
    if (tasklistProperties.getOpenSearch().isCreateSchema()) {
      schemaManager.createSchema();
      assertThat(areIndicesCreatedAfterChecks(indexPrefix, 4, 5 * 60 /*sec*/))
          .describedAs("OpenSearch %s (min %d) indices are created", indexPrefix, 5)
          .isTrue();
    }
  }

  @Override
  public void handleTestExecutionException(ExtensionContext context, Throwable throwable)
      throws Throwable {
    this.failed = true;
    throw throwable;
  }

  @Override
  public void afterEach(ExtensionContext extensionContext) {
    if (!failed) {
      final String indexPrefix = tasklistProperties.getOpenSearch().getIndexPrefix();
      TestUtil.removeAllIndices(osClient, indexPrefix);
    }
    tasklistProperties
        .getOpenSearch()
        .setIndexPrefix(TasklistOpenSearchProperties.DEFAULT_INDEX_PREFIX);
    assertMaxOpenScrollContexts(10);
  }

  public void assertMaxOpenScrollContexts(final int maxOpenScrollContexts) {
    assertThat(getOpenScrollcontextSize())
        .describedAs("There are too many open scroll contexts left.")
        .isLessThanOrEqualTo(maxOpenScrollContexts);
  }

  public void refreshIndexesInElasticsearch() {
    refreshZeebeIndices();
    refreshTasklistIndices();
  }

  public void refreshZeebeIndices() {
    try {
      zeebeOsClient
          .indices()
          .refresh(
              r -> r.index(List.of(tasklistProperties.getZeebeOpenSearch().getPrefix() + "*")));
    } catch (Exception t) {
      LOGGER.error("Could not refresh Zeebe OpenSearch indices", t);
    }
  }

  @Override
  public void refreshTasklistIndices() {
    try {
      osClient
          .indices()
          .refresh(r -> r.index(tasklistProperties.getOpenSearch().getIndexPrefix() + "*"));
    } catch (Exception t) {
      LOGGER.error("Could not refresh Tasklist OpenSearch indices", t);
    }
  }

  public void processAllRecordsAndWait(TestCheck testCheck, Object... arguments) {
    processRecordsAndWaitFor(
        recordsReaderHolder.getAllRecordsReaders(), testCheck, null, arguments);
  }

  public void processAllRecordsAndWait(
      TestCheck testCheck, Supplier<Object> supplier, Object... arguments) {
    processRecordsAndWaitFor(
        recordsReaderHolder.getAllRecordsReaders(), testCheck, supplier, arguments);
  }

  public void processRecordsWithTypeAndWait(
      ImportValueType importValueType, TestCheck testCheck, Object... arguments) {
    processRecordsAndWaitFor(getRecordsReaders(importValueType), testCheck, null, arguments);
  }

  public void processRecordsAndWaitFor(
      Collection<RecordsReader> readers,
      TestCheck testCheck,
      Supplier<Object> supplier,
      Object... arguments) {
    long shouldImportCount = 0;
    int waitingRound = 0;
    final int maxRounds = 50;
    boolean found = testCheck.test(arguments);
    final long start = System.currentTimeMillis();
    while (!found && waitingRound < maxRounds) {
      testImportListener.resetCounters();
      shouldImportCount = 0;
      try {
        if (supplier != null) {
          supplier.get();
        }
        refreshIndexesInElasticsearch();
        shouldImportCount += zeebeImporter.performOneRoundOfImportFor(readers);
      } catch (Exception e) {
        LOGGER.error(e.getMessage(), e);
      }
      long imported = testImportListener.getImported();
      int waitForImports = 0;
      // Wait for imports max 30 sec (60 * 500 ms)
      while (shouldImportCount != 0 && imported < shouldImportCount && waitForImports < 60) {
        waitForImports++;
        try {
          sleepFor(500);
          shouldImportCount += zeebeImporter.performOneRoundOfImportFor(readers);
        } catch (Exception e) {
          waitingRound = 0;
          testImportListener.resetCounters();
          shouldImportCount = 0;
          LOGGER.error(e.getMessage(), e);
        }
        imported = testImportListener.getImported();
        LOGGER.debug(" {} of {} records processed", imported, shouldImportCount);
      }
      refreshTasklistIndices();
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
      LOGGER.debug(
          "Condition {} was not met after {} rounds ({} ms).",
          testCheck.getName(),
          waitingRound,
          finishedTime);
    }
  }

  public boolean areIndicesCreatedAfterChecks(
      String indexPrefix, int minCountOfIndices, int maxChecks) {
    boolean areCreated = false;
    int checks = 0;
    while (!areCreated && checks <= maxChecks) {
      checks++;
      try {
        areCreated = areIndicesAreCreated(indexPrefix, minCountOfIndices);
      } catch (Exception t) {
        LOGGER.error(
            "OpenSearch indices (min {}) are not created yet. Waiting {}/{}",
            minCountOfIndices,
            checks,
            maxChecks);
        sleepFor(200);
      }
    }
    LOGGER.debug("OpenSearch indices are created after {} checks", checks);
    return areCreated;
  }

  private boolean areIndicesAreCreated(String indexPrefix, int minCountOfIndices)
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

  public List<RecordsReader> getRecordsReaders(ImportValueType importValueType) {
    return recordsReaderHolder.getAllRecordsReaders().stream()
        .filter(rr -> rr.getImportValueType().equals(importValueType))
        .collect(Collectors.toList());
  }

  public int getOpenScrollcontextSize() {
    int openContext = 0;
    try {
      final Set<Map.Entry<String, Stats>> nodesResult = osClient.nodes().stats().nodes().entrySet();
      for (Map.Entry<String, Stats> entryNodes : nodesResult) {
        openContext += entryNodes.getValue().indices().search().openContexts().intValue();
      }
      return openContext;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
