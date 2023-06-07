/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.util;

import static io.camunda.operate.util.ThreadUtil.sleepFor;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.entities.BatchOperationEntity;
import io.camunda.operate.entities.IncidentEntity;
import io.camunda.operate.entities.OperateEntity;
import io.camunda.operate.entities.OperationEntity;
import io.camunda.operate.entities.ProcessEntity;
import io.camunda.operate.entities.VariableEntity;
import io.camunda.operate.entities.dmn.DecisionInstanceEntity;
import io.camunda.operate.entities.dmn.definition.DecisionDefinitionEntity;
import io.camunda.operate.entities.dmn.definition.DecisionRequirementsEntity;
import io.camunda.operate.entities.listview.FlowNodeInstanceForListViewEntity;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.entities.listview.VariableForListViewEntity;
import io.camunda.operate.es.ElasticsearchConnector;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.property.OperateElasticsearchProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.ElasticsearchSchemaManager;
import io.camunda.operate.schema.indices.DecisionIndex;
import io.camunda.operate.schema.indices.DecisionRequirementsIndex;
import io.camunda.operate.schema.indices.ProcessIndex;
import io.camunda.operate.schema.templates.BatchOperationTemplate;
import io.camunda.operate.schema.templates.DecisionInstanceTemplate;
import io.camunda.operate.schema.templates.IncidentTemplate;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.schema.templates.OperationTemplate;
import io.camunda.operate.schema.templates.VariableTemplate;
import io.camunda.operate.zeebe.ImportValueType;
import io.camunda.operate.zeebeimport.RecordsReader;
import io.camunda.operate.zeebeimport.RecordsReaderHolder;
import io.camunda.operate.zeebeimport.ZeebeImporter;
import io.camunda.operate.zeebeimport.ZeebePostImporter;
import io.camunda.operate.zeebeimport.post.PostImportAction;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.client.indices.GetIndexResponse;
import org.elasticsearch.xcontent.XContentType;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

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
  private ListViewTemplate listViewTemplate;

  @Autowired
  private VariableTemplate variableTemplate;

  @Autowired
  private ProcessIndex processIndex;

  @Autowired
  private OperationTemplate operationTemplate;

  @Autowired
  private BatchOperationTemplate batchOperationTemplate;

  @Autowired
  private IncidentTemplate incidentTemplate;

  @Autowired
  private DecisionInstanceTemplate decisionInstanceTemplate;

  @Autowired
  private DecisionRequirementsIndex decisionRequirementsIndex;

  @Autowired
  private DecisionIndex decisionIndex;

  @Autowired
  protected OperateProperties operateProperties;

  @Autowired
  private ElasticsearchSchemaManager schemaManager;

  @Autowired
  protected ZeebeImporter zeebeImporter;

  @Autowired
  protected ZeebePostImporter zeebePostImporter;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  protected RecordsReaderHolder recordsReaderHolder;

  @Autowired
  private TestImportListener testImportListener;

  @Autowired
  ElasticsearchConnector esConnector;

  Map<Class<? extends OperateEntity>, String> entityToESAliasMap;

  protected boolean failed = false;

  private String indexPrefix;

  public ElasticsearchTestRule() {
  }

  public ElasticsearchTestRule(String indexPrefix) {
    this.indexPrefix = indexPrefix;
  }

  @Override
  protected void failed(Throwable e, Description description) {
    super.failed(e, description);
    this.failed = true;
  }

  @Override
  protected void starting(Description description) {
    if (indexPrefix == null) {
      indexPrefix = TestUtil.createRandomString(10) + "-operate";
    }
    operateProperties.getElasticsearch().setIndexPrefix(indexPrefix);
    if (operateProperties.getElasticsearch().isCreateSchema()) {
      schemaManager.createSchema();
      assertThat(areIndicesCreatedAfterChecks(indexPrefix, 5, 5 * 60 /*sec*/))
          .describedAs("Elasticsearch %s (min %d) indices are created", indexPrefix, 5)
          .isTrue();
    }
  }

  @Override
  protected void finished(Description description) {
    if (!failed) {
      String indexPrefix = operateProperties.getElasticsearch().getIndexPrefix();
      TestUtil.removeAllIndices(esClient, indexPrefix);
    }
    operateProperties.getElasticsearch().setIndexPrefix(OperateElasticsearchProperties.DEFAULT_INDEX_PREFIX);
    zeebePostImporter.getPostImportActions().stream().forEach(PostImportAction::clearCache);
    assertMaxOpenScrollContexts(15);
  }

  public void assertMaxOpenScrollContexts(final int maxOpenScrollContexts) {
    assertThat(getOpenScrollcontextSize())
      .describedAs("There are too many open scroll contexts left.")
      .isLessThanOrEqualTo(maxOpenScrollContexts);
  }

  public void refreshIndexesInElasticsearch() {
    refreshZeebeESIndices();
    refreshOperateESIndices();
  }

  public void refreshZeebeESIndices() {
    try {
      RefreshRequest refreshRequest = new RefreshRequest(operateProperties.getZeebeElasticsearch().getPrefix() + "*");
      zeebeEsClient.indices().refresh(refreshRequest, RequestOptions.DEFAULT);
    } catch (Exception t) {
      logger.error("Could not refresh Zeebe Elasticsearch indices", t);
    }
  }

  public void refreshOperateESIndices() {
    try {
      RefreshRequest refreshRequest = new RefreshRequest(operateProperties.getElasticsearch().getIndexPrefix() + "*");
      esClient.indices().refresh(refreshRequest, RequestOptions.DEFAULT);
    } catch (Exception t) {
      logger.error("Could not refresh Operate Elasticsearch indices", t);
    }
  }

  public void processAllRecordsAndWait(Integer maxWaitingRounds, Predicate<Object[]> predicate, Object... arguments) {
    processRecordsAndWaitFor(recordsReaderHolder.getAllRecordsReaders(), maxWaitingRounds, predicate, null, arguments);
  }

  public void processAllRecordsAndWait(Predicate<Object[]> predicate, Object... arguments) {
    processRecordsAndWaitFor(recordsReaderHolder.getAllRecordsReaders(), predicate, null, arguments);
  }

  public void processAllRecordsAndWait(Predicate<Object[]> predicate, Supplier<Object> supplier, Object... arguments) {
    processRecordsAndWaitFor(recordsReaderHolder.getAllRecordsReaders(), predicate, supplier, arguments);
  }

  public void processRecordsWithTypeAndWait(ImportValueType importValueType,Predicate<Object[]> predicate, Object... arguments) {
    processRecordsAndWaitFor(getRecordsReaders(importValueType), predicate, null, arguments);
  }

  public void processRecordsAndWaitFor(Collection<RecordsReader> readers,
      Predicate<Object[]> predicate, Supplier<Object> supplier, Object... arguments) {
    processRecordsAndWaitFor(readers, 50, predicate, supplier, arguments);
  }

  public void processRecordsAndWaitFor(Collection<RecordsReader> readers, Integer maxWaitingRounds,
      Predicate<Object[]> predicate, Supplier<Object> supplier, Object... arguments) {
    int waitingRound = 0, maxRounds = maxWaitingRounds;
    boolean found = predicate.test(arguments);
    long start = System.currentTimeMillis();
    while (!found && waitingRound < maxRounds) {
      testImportListener.resetCounters();
      try {
        if (supplier != null) {
          supplier.get();
        }
        refreshIndexesInElasticsearch();
        zeebeImporter.performOneRoundOfImportFor(readers);
        refreshOperateESIndices();
        runPostImportActions();

      } catch (Exception e) {
        logger.error(e.getMessage(), e);
      }
      int waitForImports = 0;
      // Wait for imports max 30 sec (60 * 500 ms)
      while (testImportListener.getImportedCount() < testImportListener.getScheduledCount()
          && waitForImports < 60) {
        waitForImports++;
        try {
          sleepFor(2000);
          zeebeImporter.performOneRoundOfImportFor(readers);
          refreshOperateESIndices();
          runPostImportActions();

        } catch (Exception e) {
          waitingRound = 0;
          testImportListener.resetCounters();
          logger.error(e.getMessage(), e);
        }
        logger.debug(" {} of {} imports processed", testImportListener.getImportedCount(),
            testImportListener.getScheduledCount());
      }
      refreshOperateESIndices();
      found = predicate.test(arguments);
      if (!found) {
        sleepFor(2000);
        waitingRound++;
      }
    }
    long finishedTime = System.currentTimeMillis() - start;

    if(found) {
      logger.debug("Conditions met in round {} ({} ms).", waitingRound,finishedTime );
    }else {
      logger.debug("Conditions not met after {} rounds ({} ms).", waitingRound, finishedTime);
//      throw new TestPrerequisitesFailedException("Conditions not met.");
    }
  }

  public void runPostImportActions() {
    if (zeebePostImporter.getPostImportActions().size() == 0) {
      zeebePostImporter.initPostImporters();
    }
    for (PostImportAction action: zeebePostImporter.getPostImportActions()) {
      try {
        action.performOneRound();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  public boolean areIndicesCreatedAfterChecks(String indexPrefix, int minCountOfIndices,int maxChecks) {
    boolean areCreated = false;
    int checks = 0;
    while (!areCreated && checks <= maxChecks) {
      checks++;
      try {
        areCreated = areIndicesAreCreated(indexPrefix, minCountOfIndices);
      } catch (Exception t) {
        logger.error("Elasticsearch indices (min {}) are not created yet. Waiting {}/{}",minCountOfIndices, checks, maxChecks);
        sleepFor(200);
      }
    }
    logger.debug("Elasticsearch indices are created after {} checks", checks);
    return areCreated;
  }

  private boolean areIndicesAreCreated(String indexPrefix, int minCountOfIndices)
      throws IOException {
    GetIndexResponse response = esClient.indices().get(
        new GetIndexRequest(indexPrefix + "*")
            .indicesOptions(IndicesOptions.fromOptions(true, false, true, false)),
        RequestOptions.DEFAULT);
    String[] indices = response.getIndices();
    return indices != null && indices.length >= minCountOfIndices;
  }

  public List<RecordsReader> getRecordsReaders(ImportValueType importValueType) {
    return recordsReaderHolder.getAllRecordsReaders().stream()
        .filter(rr -> rr.getImportValueType().equals(importValueType)).collect(Collectors.toList());
  }

  public void persistNew(OperateEntity... entitiesToPersist) {
    try {
      persistOperateEntitiesNew(Arrays.asList(entitiesToPersist));
    } catch (PersistenceException e) {
      logger.error("Unable to persist entities: " + e.getMessage(), e);
      throw new RuntimeException(e);
    }
    refreshIndexesInElasticsearch();
  }

  public void persistOperateEntitiesNew(List<? extends OperateEntity> operateEntities) throws PersistenceException {
    try {
      BulkRequest bulkRequest = new BulkRequest();
      for (OperateEntity entity : operateEntities) {
        final String alias = getEntityToESAliasMap().get(entity.getClass());
        if (alias == null) {
          throw new RuntimeException("Index not configured for " + entity.getClass().getName());
        }
        final IndexRequest indexRequest = new IndexRequest(alias)
            .id(entity.getId())
            .source(objectMapper.writeValueAsString(entity), XContentType.JSON);
        if (entity instanceof FlowNodeInstanceForListViewEntity) {
          indexRequest.routing(((FlowNodeInstanceForListViewEntity)entity).getProcessInstanceKey().toString());
        }
        if (entity instanceof VariableForListViewEntity) {
          indexRequest.routing(((VariableForListViewEntity)entity).getProcessInstanceKey().toString());
        }
        bulkRequest.add(indexRequest);
      }
      ElasticsearchUtil.processBulkRequest(esClient, bulkRequest, true, operateProperties.getElasticsearch().getBulkRequestMaxSizeInBytes());
    } catch (Exception ex) {
      throw new PersistenceException(ex);
    }

  }

  public Map<Class<? extends OperateEntity>, String> getEntityToESAliasMap(){
    if (entityToESAliasMap == null) {
      entityToESAliasMap = new HashMap<>();
      entityToESAliasMap.put(ProcessEntity.class, processIndex.getFullQualifiedName());
      entityToESAliasMap.put(IncidentEntity.class, incidentTemplate.getFullQualifiedName());
      entityToESAliasMap.put(ProcessInstanceForListViewEntity.class, listViewTemplate.getFullQualifiedName());
      entityToESAliasMap.put(FlowNodeInstanceForListViewEntity.class, listViewTemplate.getFullQualifiedName());
      entityToESAliasMap.put(VariableForListViewEntity.class, listViewTemplate.getFullQualifiedName());
      entityToESAliasMap.put(VariableEntity.class, variableTemplate.getFullQualifiedName());
      entityToESAliasMap.put(OperationEntity.class, operationTemplate.getFullQualifiedName());
      entityToESAliasMap.put(BatchOperationEntity.class, batchOperationTemplate.getFullQualifiedName());
      entityToESAliasMap.put(DecisionInstanceEntity.class, decisionInstanceTemplate.getFullQualifiedName());
      entityToESAliasMap.put(DecisionRequirementsEntity.class, decisionRequirementsIndex.getFullQualifiedName());
      entityToESAliasMap.put(DecisionDefinitionEntity.class, decisionIndex.getFullQualifiedName());
    }
    return entityToESAliasMap;
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
    } catch (Exception e) {
      logger.error("Couldn't retrieve json object from elasticsearch. Return Optional.Empty.",e);
      return Optional.empty();
    }
  }

  public String getIndexPrefix() {
    return indexPrefix;
  }
}
