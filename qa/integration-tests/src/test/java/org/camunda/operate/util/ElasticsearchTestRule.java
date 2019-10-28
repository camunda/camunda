/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.util;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.camunda.operate.TestImportListener;
import org.camunda.operate.entities.IncidentEntity;
import org.camunda.operate.entities.OperateEntity;
import org.camunda.operate.entities.OperationEntity;
import org.camunda.operate.entities.WorkflowEntity;
import org.camunda.operate.entities.listview.ActivityInstanceForListViewEntity;
import org.camunda.operate.entities.listview.VariableForListViewEntity;
import org.camunda.operate.entities.listview.WorkflowInstanceForListViewEntity;
import org.camunda.operate.es.ElasticsearchConnector;
import org.camunda.operate.es.ElasticsearchSchemaManager;
import org.camunda.operate.es.schema.indices.WorkflowIndex;
import org.camunda.operate.es.schema.templates.IncidentTemplate;
import org.camunda.operate.es.schema.templates.ListViewTemplate;
import org.camunda.operate.es.schema.templates.OperationTemplate;
import org.camunda.operate.exceptions.OperateRuntimeException;
import org.camunda.operate.exceptions.PersistenceException;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.zeebe.ImportValueType;
import org.camunda.operate.zeebeimport.RecordsReader;
import org.camunda.operate.zeebeimport.RecordsReaderHolder;
import org.camunda.operate.zeebeimport.ZeebeImporter;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.client.indices.GetIndexResponse;
import org.elasticsearch.common.xcontent.XContentType;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.operate.property.OperateElasticsearchProperties.DEFAULT_INDEX_PREFIX;
import static org.camunda.operate.util.CollectionUtil.*;
import static org.camunda.operate.util.ThreadUtil.*;

public class ElasticsearchTestRule extends TestWatcher {

  protected static final Logger logger = LoggerFactory.getLogger(ElasticsearchTestRule.class);

  @Autowired
  protected RestHighLevelClient esClient;

  @Autowired
  @Qualifier("zeebeEsClient")
  protected RestHighLevelClient zeebeEsClient;

  @Autowired
  private ListViewTemplate listViewTemplate;

  @Autowired
  private WorkflowIndex workflowIndex;

  @Autowired
  private OperationTemplate operationTemplate;

  @Autowired
  private IncidentTemplate incidentTemplate;

  @Autowired
  protected OperateProperties operateProperties;

  @Autowired
  private ElasticsearchSchemaManager elasticsearchSchemaManager;

  @Autowired
  protected ZeebeImporter zeebeImporter;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  protected RecordsReaderHolder recordsReaderHolder;

  @Autowired
  TestImportListener testImportListener;

  @Autowired
  ElasticsearchConnector esConnector;

  Map<Class<? extends OperateEntity>, String> entityToESAliasMap;

  protected boolean failed = false;
  
  private int waitingRound = 1;
  
  @Override
  protected void failed(Throwable e, Description description) {
    super.failed(e, description);
    this.failed = true;
  }

  @Override
  protected void starting(Description description) {
    String indexPrefix = TestUtil.createRandomString(10) + "-operate";
    operateProperties.getElasticsearch().setIndexPrefix(indexPrefix);
    elasticsearchSchemaManager.createIndices();
    elasticsearchSchemaManager.createTemplates();
    assertThat(areIndicesCreatedAfterChecks(indexPrefix,5, 5 * 60 /*sec*/))
      .describedAs("Elasticsearch %s (min %d) indices are created",indexPrefix,5)
      .isTrue();
  }

  @Override
  protected void finished(Description description) {
    if (!failed) {
      String indexPrefix = operateProperties.getElasticsearch().getIndexPrefix();
      TestUtil.removeAllIndices(esClient,indexPrefix);
      assertThat(areIndicesNotExistsAfterChecks(indexPrefix,10 * 60 /*sec*/))
        .describedAs("Elasticsearch '%s' indexes are deleted.",indexPrefix)
        .isTrue();
    }
    operateProperties.getElasticsearch().setIndexPrefix(DEFAULT_INDEX_PREFIX);
  }

//  public void assertZeebeESIsRunning() {
//    assertThat(esConnector.checkHealth(zeebeEsClient, true)).describedAs("Zeebe Elasticsearch is running").isTrue();
//  }

//  public void assertOperateESIsRunning() {
//    assertThat(esConnector.checkHealth(esClient, true)).describedAs("Operator Elasticsearch is running").isTrue();
//  }

  public void refreshIndexesInElasticsearch() {
    refreshZeebeESIndices();
    refreshOperateESIndices();
  }

  public void refreshZeebeESIndices() {
    try {
      RefreshRequest refreshRequest = new RefreshRequest(operateProperties.getZeebeElasticsearch().getPrefix() + "*");
      zeebeEsClient.indices().refresh(refreshRequest, RequestOptions.DEFAULT);
    } catch (Throwable t) {
      logger.error("Could not refresh Zeebe Elasticsearch indices", t);
    }
  }

  public void refreshOperateESIndices() {
    try {
      RefreshRequest refreshRequest = new RefreshRequest(operateProperties.getElasticsearch().getIndexPrefix() + "*");
      esClient.indices().refresh(refreshRequest, RequestOptions.DEFAULT);
    } catch (Throwable t) {
      logger.error("Could not refresh Operate Elasticsearch indices", t);
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
    int maxRounds = 500;
    boolean found = predicate.test(arguments);
    long start = System.currentTimeMillis();
    while (!found && waitingRound < maxRounds) {
      testImportListener.resetCounters();
      shouldImportCount = 0;
      try {
        if (supplier != null) {
          supplier.get();
        }
        refreshZeebeESIndices();
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
          refreshZeebeESIndices();
          shouldImportCount += zeebeImporter.performOneRoundOfImportFor(readers);
        } catch (Exception e) {
          waitingRound = 1;
          testImportListener.resetCounters();
          shouldImportCount = 0;
          logger.error(e.getMessage(), e);
        }
        imported = testImportListener.getImported();
        logger.debug(" {} of {} imports processed", imported, shouldImportCount);
      }
      refreshOperateESIndices();
      found = predicate.test(arguments);
      waitingRound++;
    }
    if(found) {
      logger.debug("Conditions met in round {} ({} ms).", waitingRound--, System.currentTimeMillis()-start);
    }
    waitingRound = 1;
    testImportListener.resetCounters();
    if (waitingRound >=  maxRounds) {
      throw new OperateRuntimeException("Timeout exception");
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
    List<String> indices = List.of(response.getIndices());
    return filter(indices,index -> index.contains(indexPrefix)).size() > minCountOfIndices;
  }

  public boolean areIndicesNotExistsAfterChecks(String indexPrefix,int maxChecks) {
    boolean isEmpty = false;
    int checks = 0;
    while (!isEmpty && checks <= maxChecks) {
      checks++;
      isEmpty = areIndicesNotExists(indexPrefix);
      logger.error("Elasticsearch indices are not empty yet. Waiting {}/{}", checks, maxChecks);
      sleepFor(100);
    }
    logger.debug("Elasticsearch indices are empty after {} checks", checks);
    return isEmpty;
  }

  private boolean areIndicesNotExists(String indexPrefix) {
    boolean isEmpty = false;
    try {
      esClient.indices().get(new GetIndexRequest(indexPrefix + "*"), RequestOptions.DEFAULT);
    } catch (ElasticsearchStatusException e) {
      isEmpty = true;
    } catch (Throwable t) {
      logger.error(t.getMessage(),t);
    }
    return isEmpty;
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
        final IndexRequest indexRequest =
          new IndexRequest(alias, ElasticsearchUtil.ES_INDEX_TYPE, entity.getId())
            .source(objectMapper.writeValueAsString(entity), XContentType.JSON);
        if (entity instanceof ActivityInstanceForListViewEntity) {
          indexRequest.routing(((ActivityInstanceForListViewEntity)entity).getWorkflowInstanceKey().toString());
        }
        if (entity instanceof VariableForListViewEntity) {
          indexRequest.routing(((VariableForListViewEntity)entity).getWorkflowInstanceKey().toString());
        }
        bulkRequest.add(indexRequest);
      }
      ElasticsearchUtil.processBulkRequest(esClient, bulkRequest, true);
    } catch (Exception ex) {
      throw new PersistenceException(ex);
    }

  }

  public Map<Class<? extends OperateEntity>, String> getEntityToESAliasMap(){
    if (entityToESAliasMap == null) {
      entityToESAliasMap = new HashMap<>();
      entityToESAliasMap.put(WorkflowEntity.class, workflowIndex.getAlias());
      entityToESAliasMap.put(IncidentEntity.class, incidentTemplate.getAlias());
      entityToESAliasMap.put(WorkflowInstanceForListViewEntity.class, listViewTemplate.getAlias());
      entityToESAliasMap.put(ActivityInstanceForListViewEntity.class, listViewTemplate.getAlias());
      entityToESAliasMap.put(VariableForListViewEntity.class, listViewTemplate.getAlias());
      entityToESAliasMap.put(OperationEntity.class, operationTemplate.getAlias());
    }
    return entityToESAliasMap;
  }

}
