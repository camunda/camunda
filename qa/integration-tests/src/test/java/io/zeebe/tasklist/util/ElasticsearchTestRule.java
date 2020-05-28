/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.util;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.client.indices.GetIndexResponse;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.tasklist.es.ElasticsearchSchemaManager;
import io.zeebe.tasklist.property.TasklistElasticsearchProperties;
import io.zeebe.tasklist.property.TasklistProperties;
import io.zeebe.tasklist.zeebe.ImportValueType;
import io.zeebe.tasklist.zeebeimport.RecordsReader;
import io.zeebe.tasklist.zeebeimport.RecordsReaderHolder;
import io.zeebe.tasklist.zeebeimport.ZeebeImporter;
import static io.zeebe.tasklist.util.ThreadUtil.sleepFor;
import static org.assertj.core.api.Assertions.assertThat;

public class ElasticsearchTestRule extends TestWatcher {

  protected static final Logger logger = LoggerFactory.getLogger(ElasticsearchTestRule.class);
  
  // Scroll contexts constants 
  private static final String OPEN_SCROLL_CONTEXT_FIELD = "open_contexts";
  // Path to find search statistics for all indexes
  private static final String PATH_SEARCH_STATISTICS = "/_nodes/stats/indices/search?filter_path=nodes.*.indices.search";

  @Autowired
  protected RestHighLevelClient esClient;

  @Autowired
  @Qualifier("zeebeEsClient")
  protected RestHighLevelClient zeebeEsClient;

  @Autowired
  protected TasklistProperties tasklistProperties;

  @Autowired
  private ElasticsearchSchemaManager elasticsearchSchemaManager;

  @Autowired
  protected ZeebeImporter zeebeImporter;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  protected RecordsReaderHolder recordsReaderHolder;

  @Autowired
  private TestImportListener testImportListener;

  protected boolean failed = false;

  private String indexPrefix;

  public ElasticsearchTestRule() {
  }

  @Override
  protected void failed(Throwable e, Description description) {
    super.failed(e, description);
    this.failed = true;
  }

  @Override
  protected void starting(Description description) {
    if (indexPrefix == null) {
      indexPrefix = TestUtil.createRandomString(10) + "-tasklist";
    }
    tasklistProperties.getElasticsearch().setIndexPrefix(indexPrefix);
    if (tasklistProperties.getElasticsearch().isCreateSchema()) {
      elasticsearchSchemaManager.createSchema();
      assertThat(areIndicesCreatedAfterChecks(indexPrefix, 4, 5 * 60 /*sec*/))
          .describedAs("Elasticsearch %s (min %d) indices are created", indexPrefix, 5)
          .isTrue();
    }
  }

  @Override
  protected void finished(Description description) {
    if (!failed) {
      String indexPrefix = tasklistProperties.getElasticsearch().getIndexPrefix();
      TestUtil.removeAllIndices(esClient,indexPrefix);
    }
    tasklistProperties.getElasticsearch().setIndexPrefix(TasklistElasticsearchProperties.DEFAULT_INDEX_PREFIX);
    assertMaxOpenScrollContexts(10);
  }
  
  public void assertMaxOpenScrollContexts(final int maxOpenScrollContexts) {
    assertThat(getOpenScrollcontextSize())
      .describedAs("There are too many open scroll contexts left.")
      .isLessThanOrEqualTo(maxOpenScrollContexts);
  }

  public void refreshIndexesInElasticsearch() {
    refreshZeebeESIndices();
    refreshTasklistESIndices();
  }

  public void refreshZeebeESIndices() {
    try {
      RefreshRequest refreshRequest = new RefreshRequest(tasklistProperties.getZeebeElasticsearch().getPrefix() + "*");
      zeebeEsClient.indices().refresh(refreshRequest, RequestOptions.DEFAULT);
    } catch (Throwable t) {
      logger.error("Could not refresh Zeebe Elasticsearch indices", t);
    }
  }

  public void refreshTasklistESIndices() {
    try {
      RefreshRequest refreshRequest = new RefreshRequest(tasklistProperties.getElasticsearch().getIndexPrefix() + "*");
      esClient.indices().refresh(refreshRequest, RequestOptions.DEFAULT);
    } catch (Throwable t) {
      logger.error("Could not refresh Tasklist Elasticsearch indices", t);
    }
  }

  public void processAllRecordsAndWait(Predicate<Object[]> predicate, Object... arguments) {
    processRecordsAndWaitFor(recordsReaderHolder.getActiveRecordsReaders(), predicate, null, arguments);
  }

  public void processAllRecordsAndWait(Predicate<Object[]> predicate, Supplier<Object> supplier, Object... arguments) {
    processRecordsAndWaitFor(recordsReaderHolder.getActiveRecordsReaders(), predicate, supplier, arguments);
  }

  public void processRecordsWithTypeAndWait(ImportValueType importValueType,Predicate<Object[]> predicate, Object... arguments) {
    processRecordsAndWaitFor(getRecordsReaders(importValueType), predicate, null, arguments);
  }

  public void processRecordsAndWaitFor(Collection<RecordsReader> readers,Predicate<Object[]> predicate, Supplier<Object> supplier, Object... arguments) {
    long shouldImportCount = 0;
    int waitingRound = 0, maxRounds = 50;
    boolean found = predicate.test(arguments);
    long start = System.currentTimeMillis();
    while (!found && waitingRound < maxRounds) {
      testImportListener.resetCounters();
      shouldImportCount = 0;
      try {
        if (supplier != null) {
          supplier.get();
        }
        refreshIndexesInElasticsearch(); 
        shouldImportCount +=  zeebeImporter.performOneRoundOfImportFor(readers);
      } catch (Exception e) {
        logger.error(e.getMessage(), e);
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
          logger.error(e.getMessage(), e);
        }
        imported = testImportListener.getImported();
        logger.debug(" {} of {} imports processed", imported, shouldImportCount);
      }
      refreshTasklistESIndices();
      found = predicate.test(arguments);
      if (!found) {
        sleepFor(500);
        waitingRound++;
      }
    }
    long finishedTime = System.currentTimeMillis() - start;
    
    if(found) {
      logger.debug("Conditions met in round {} ({} ms).", waitingRound,finishedTime );
    }else {
      //throw new OperateRuntimeException(String.format("Conditions not met after %s rounds (%s ms).", waitingRound, finishedTime));
      logger.debug("Conditions not met after %s rounds (%s ms).", waitingRound, finishedTime);
    }
  }

  public boolean areIndicesCreatedAfterChecks(String indexPrefix, int minCountOfIndices,int maxChecks) {
    boolean areCreated = false;
    int checks = 0;
    while (!areCreated && checks <= maxChecks) {
      checks++;
      try {
        areCreated = areIndicesAreCreated(indexPrefix, minCountOfIndices);
      } catch (Throwable t) {
        logger.error("Elasticsearch indices (min {}) are not created yet. Waiting {}/{}",minCountOfIndices, checks, maxChecks);
        sleepFor(200);
      }
    }
    logger.debug("Elasticsearch indices are created after {} checks", checks);
    return areCreated;
  }

  private boolean areIndicesAreCreated(String indexPrefix, int minCountOfIndices) throws IOException {
    GetIndexResponse response = esClient.indices().get(new GetIndexRequest(indexPrefix + "*"), RequestOptions.DEFAULT);
    String[] indices = response.getIndices();
    return indices != null && indices.length >= minCountOfIndices; 
  }

  public List<RecordsReader> getRecordsReaders(ImportValueType importValueType) {
    return recordsReaderHolder.getAllRecordsReaders().stream()
        .filter(rr -> rr.getImportValueType().equals(importValueType)).collect(Collectors.toList());
  }

  public int getOpenScrollcontextSize() {
    return getIntValueForJSON(PATH_SEARCH_STATISTICS, OPEN_SCROLL_CONTEXT_FIELD, 0);
  }

  public int getIntValueForJSON(final String path,final String fieldname,final int defaultValue) {
    Optional<JsonNode> jsonNode = getJsonFor(path);
    if(jsonNode.isPresent()) {
      JsonNode field = jsonNode.get().findValue(fieldname);
      if(field != null) {
        return field.asInt(defaultValue);
      }
    }
    return defaultValue;
  }

  public Optional<JsonNode> getJsonFor(final String path) {
    try {      
      ObjectMapper objectMapper = new ObjectMapper();
      Response response = esClient.getLowLevelClient().performRequest(new Request("GET",path));
      return Optional.of(objectMapper.readTree(response.getEntity().getContent()));
    } catch (Throwable e) {
      logger.error("Couldn't retrieve json object from elasticsearch. Return Optional.Empty.",e);
      return Optional.empty();
    }
  }
}
