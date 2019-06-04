/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.util;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.camunda.operate.entities.IncidentEntity;
import org.camunda.operate.entities.OperateEntity;
import org.camunda.operate.entities.OperationEntity;
import org.camunda.operate.entities.WorkflowEntity;
import org.camunda.operate.entities.listview.ActivityInstanceForListViewEntity;
import org.camunda.operate.entities.listview.VariableForListViewEntity;
import org.camunda.operate.entities.listview.WorkflowInstanceForListViewEntity;
import org.camunda.operate.es.ElasticsearchSchemaManager;
import org.camunda.operate.es.schema.indices.WorkflowIndex;
import org.camunda.operate.es.schema.templates.IncidentTemplate;
import org.camunda.operate.es.schema.templates.ListViewTemplate;
import org.camunda.operate.es.schema.templates.OperationTemplate;
import org.camunda.operate.exceptions.PersistenceException;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.zeebeimport.ImportValueType;
import org.camunda.operate.zeebeimport.RecordsReader;
import org.camunda.operate.zeebeimport.RecordsReaderHolder;
import org.camunda.operate.zeebeimport.ZeebeImporter;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.IndexNotFoundException;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.camunda.operate.property.OperateElasticsearchProperties.DEFAULT_INDEX_PREFIX;

public class ElasticsearchTestRule extends TestWatcher {

  private static final Logger logger = LoggerFactory.getLogger(ElasticsearchTestRule.class);

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
  private OperateProperties operateProperties;

  @Autowired
  private ElasticsearchSchemaManager elasticsearchSchemaManager;

  @Autowired
  private ZeebeImporter zeebeImporter;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private RecordsReaderHolder recordsReaderHolder;

  Map<Class<? extends OperateEntity>, String> entityToESAliasMap;

  protected boolean failed = false;
  
  int waitingRound = 1;
  
  @Override
  protected void failed(Throwable e, Description description) {
    super.failed(e, description);
    this.failed = true;
  }

  @Override
  protected void starting(Description description) {
    operateProperties.getElasticsearch().setIndexPrefix(TestUtil.createRandomString(10) + "-operate");
    elasticsearchSchemaManager.createIndices();
    elasticsearchSchemaManager.createTemplates();
  }

  @Override
  protected void finished(Description description) {
    if (!failed) {
      TestUtil.removeAllIndices(esClient, operateProperties.getElasticsearch().getIndexPrefix());
    }
    operateProperties.getElasticsearch().setIndexPrefix(DEFAULT_INDEX_PREFIX);
  }


  public void refreshIndexesInElasticsearch() {
    try {
      esClient.indices().refresh(new RefreshRequest(), RequestOptions.DEFAULT);
      zeebeEsClient.indices().refresh(new RefreshRequest(), RequestOptions.DEFAULT);
    } catch (IndexNotFoundException | IOException e) {
      logger.error(e.getMessage(), e);
      //      nothing to do
    }
  }

  public void processAllRecordsAndWait(Runnable importer, Predicate<Object[]> waitTill, Object... arguments) {
    int maxRounds = 500;
    boolean found = waitTill.test(arguments);
    long start = System.currentTimeMillis();
    while (!found && waitingRound < maxRounds) {
      zeebeImporter.resetCounters();
      try {
        importer.run();
      } catch (Exception e) {
        logger.error(e.getMessage(), e);
      }
      long shouldImportCount = zeebeImporter.getScheduledImportCount();
      long imported = zeebeImporter.getImportedCount();
      while (shouldImportCount != 0 && imported < shouldImportCount) {
        try {
          Thread.sleep(500L);
          importer.run();
        } catch (Exception e) {
          waitingRound = 1;
          zeebeImporter.resetCounters();
          logger.error(e.getMessage(), e);
        }
        shouldImportCount = zeebeImporter.getScheduledImportCount();
        imported = zeebeImporter.getImportedCount();
      }
      if(shouldImportCount!=0) {
        logger.debug("Imported {} of {} records", imported, shouldImportCount);
      }
      found = waitTill.test(arguments);
      waitingRound++;
    }
    if(found) {
      logger.debug("Conditions met in round {} ({} ms).", waitingRound--, System.currentTimeMillis()-start);
    }
    waitingRound = 1;
    zeebeImporter.resetCounters();
    if (waitingRound >=  maxRounds) {
      logTimeout();
    }
  }

  public void processAllRecordsAndWait(Predicate<Object[]> waitTill, Object... arguments) {
    processAllRecordsAndWait(() -> {
      try {
        zeebeImporter.performOneRoundOfImport();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }, waitTill, arguments);
  }

  public void processAllRecordsAndWait(ImportValueType importValueType, Predicate<Object[]> waitTill, Object... arguments) {
    processAllRecordsAndWait(() -> {
      try {
        importOneType(importValueType);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }, waitTill, arguments);

  }

  private int importOneType(ImportValueType importValueType) throws IOException {
    List<RecordsReader> readers = getRecordsReaders(importValueType);
    int count = 0;
    for (RecordsReader reader: readers) {
      count += zeebeImporter.importOneBatch(reader);
    }
    return count;
  }

  private List<RecordsReader> getRecordsReaders(ImportValueType importValueType) {
    return recordsReaderHolder.getAllRecordsReaders().stream()
        .filter(rr -> rr.getImportValueType().equals(importValueType)).collect(Collectors.toList());
  }

  private void logTimeout() {
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    new Throwable().printStackTrace(pw);
    logger.warn("Condition not reached: " + sw.toString());
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
          indexRequest.routing(((ActivityInstanceForListViewEntity)entity).getWorkflowInstanceId());
        }
        if (entity instanceof VariableForListViewEntity) {
          indexRequest.routing(((VariableForListViewEntity)entity).getWorkflowInstanceId());
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
