package org.camunda.operate.util;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.camunda.operate.entities.OperateEntity;
import org.camunda.operate.entities.listview.ActivityInstanceForListViewEntity;
import org.camunda.operate.entities.listview.WorkflowInstanceForListViewEntity;
import org.camunda.operate.es.ElasticsearchSchemaManager;
import org.camunda.operate.es.schema.indices.IndexCreator;
import org.camunda.operate.es.schema.templates.ListViewTemplate;
import org.camunda.operate.exceptions.PersistenceException;
import org.camunda.operate.property.OperateElasticsearchProperties;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.zeebeimport.ElasticsearchBulkProcessor;
import org.camunda.operate.zeebeimport.ZeebeESImporter;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.IndexNotFoundException;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ElasticsearchTestRule extends ExternalResource {

  private static final Logger logger = LoggerFactory.getLogger(ElasticsearchTestRule.class);

  @Autowired
  protected TransportClient esClient;

  @Autowired
  @Qualifier("zeebeEsClient")
  protected TransportClient zeebeEsClient;

  @Autowired
  private List<IndexCreator> typeMappingCreators;

  @Autowired
  private ListViewTemplate listViewTemplate;

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  private ElasticsearchSchemaManager elasticsearchSchemaManager;

  @Autowired
  private ZeebeESImporter zeebeESImporter;

  @Autowired
  private ElasticsearchBulkProcessor elasticsearchBulkProcessor;

  @Autowired
  private ObjectMapper objectMapper;

  private boolean haveToClean = true;

  private String workflowIndexName;
  private String workflowInstanceIndexName;
  private String operationIndexName;
  private String listViewIndexName;
  private String eventIndexName;
  private String importPositionIndexName;

  Map<Class<? extends OperateEntity>, String> entityToESAliasMap;

  @Override
  public void before() {
    final String indexSuffix = TestUtil.createRandomString(10);
    workflowIndexName = OperateElasticsearchProperties.WORKFLOW_INDEX_PATTERN + indexSuffix + "_";
    workflowInstanceIndexName = OperateElasticsearchProperties.WORKFLOW_INSTANCE_INDEX_PATTERN + indexSuffix + "_";
    listViewIndexName = OperateElasticsearchProperties.LIST_VIEW_INDEX_PATTERN + indexSuffix + "_";
    operationIndexName = OperateElasticsearchProperties.OPERATION_INDEX_PATTERN + indexSuffix + "_";
    eventIndexName = OperateElasticsearchProperties.EVENT_INDEX_PATTERN + indexSuffix + "_";
    importPositionIndexName = OperateElasticsearchProperties.IMPORT_POSITION_INDEX_PATTERN + indexSuffix + "_";
    operateProperties.getElasticsearch().setWorkflowIndexName(workflowIndexName);
    operateProperties.getElasticsearch().setWorkflowInstanceIndexName(workflowInstanceIndexName);
    operateProperties.getElasticsearch().setListViewIndexName(listViewIndexName);
    operateProperties.getElasticsearch().setOperationIndexName(operationIndexName);
    operateProperties.getElasticsearch().setEventIndexName(eventIndexName);
    operateProperties.getElasticsearch().setImportPositionIndexName(importPositionIndexName);

    //make aliases differ from index names
    operateProperties.getElasticsearch().setWorkflowAlias(workflowIndexName + "alias");
    operateProperties.getElasticsearch().setImportPositionAlias(importPositionIndexName + "alias");

    elasticsearchSchemaManager.createIndices();
    elasticsearchSchemaManager.createTemplates();

  }

  @Override
  public void after() {
    removeAllIndices();
    operateProperties.getElasticsearch().setWorkflowIndexName(OperateElasticsearchProperties.WORKFLOW_INDEX_PATTERN + "_");
    operateProperties.getElasticsearch().setWorkflowInstanceIndexName(OperateElasticsearchProperties.WORKFLOW_INSTANCE_INDEX_PATTERN + "_");
    operateProperties.getElasticsearch().setEventIndexName(OperateElasticsearchProperties.EVENT_INDEX_PATTERN + "_");
    operateProperties.getElasticsearch().setListViewIndexName(OperateElasticsearchProperties.LIST_VIEW_INDEX_PATTERN + "_");
    operateProperties.getElasticsearch().setOperationIndexName(OperateElasticsearchProperties.OPERATION_INDEX_PATTERN + "_");

    operateProperties.getElasticsearch().setWorkflowAlias(OperateElasticsearchProperties.WORKFLOW_INDEX_PATTERN + "_alias");
    operateProperties.getElasticsearch().setImportPositionAlias(OperateElasticsearchProperties.IMPORT_POSITION_INDEX_PATTERN + "_alias");

//    if (haveToClean) {
//      logger.info("cleaning up elasticsearch on finish");
//      cleanAndVerify();
//      refreshIndexesInElasticsearch();
//    }
  }

  public void removeAllIndices() {
    logger.info("Removing indices");
    esClient.admin().indices().delete(new DeleteIndexRequest(workflowIndexName, workflowInstanceIndexName, eventIndexName, importPositionIndexName));
  }

//  public void cleanAndVerify() {
//    assureElasticsearchIsClean();
//    cleanUpElasticSearch();
//  }


//  public void cleanUpElasticSearch() {
//    for (TypeMappingCreator mapping : typeMappingCreators) {
//      BulkByScrollResponse response = DeleteByQueryAction.INSTANCE.newRequestBuilder(esClient)
//        .refresh(true)
//        .filter(matchAllQuery())
//        .source(mapping.getIndexName())
//        .execute()
//        .actionGet();
//      logger.info("[{}] documents are removed from the index [{}]", response.getDeleted(), mapping.getIndexName());
//    }
//  }
//
  public void refreshIndexesInElasticsearch() {
    try {
      esClient.admin().indices()
        .prepareRefresh()
        .get();

      zeebeEsClient.admin().indices()
        .prepareRefresh()
        .get();
    } catch (IndexNotFoundException e) {
      logger.error(e.getMessage(), e);
      //      nothing to do
    }
  }

  public void processAllEvents() {
    processAllEvents(1);
  }

  public void processAllEvents(int expectedMinEventsCount) {
    try {
      int entitiesCount;
      int totalCount = 0;
      int emptyAttempts = 0;
      do {
        Thread.sleep(1000L);
        entitiesCount = zeebeESImporter.processNextEntitiesBatch();
        totalCount += entitiesCount;
        if (entitiesCount > 0) {
          emptyAttempts = 0;
        } else {
          emptyAttempts++;
        }
      } while(totalCount < expectedMinEventsCount && emptyAttempts < 5);
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
    }
  }

  public void processAllEvents(int expectedMinEventsCount, ZeebeESImporter.ImportValueType importValueType) {
    try {
      int entitiesCount;
      int totalCount = 0;
      int emptyAttempts = 0;
      do {
        Thread.sleep(1000L);
        entitiesCount = zeebeESImporter.processNextEntitiesBatch(0, importValueType);
        totalCount += entitiesCount;
        if (entitiesCount > 0) {
          emptyAttempts = 0;
        } else {
          emptyAttempts++;
        }
      } while(totalCount < expectedMinEventsCount && emptyAttempts < 5);
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
    }
  }

  public void processAllEventsAndWait(Predicate<Object[]> waitTill, Object... arguments) {
    try {
      int emptyAttempts = 0;
      boolean found;
      do {
        Thread.sleep(200L);
        zeebeESImporter.processNextEntitiesBatch();
        found = waitTill.test(arguments);
        if (!found) {
          emptyAttempts++;
          Thread.sleep(1000L);
        }
      } while(!found && emptyAttempts < 5);
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
    }
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

  public void persist(OperateEntity... entitiesToPersist) {
    try {
      elasticsearchBulkProcessor.persistOperateEntities(Arrays.asList(entitiesToPersist));
    } catch (PersistenceException e) {
      logger.error("Unable to persist entities: " + e.getMessage(), e);
      throw new RuntimeException(e);
    }
    refreshIndexesInElasticsearch();
  }

//  private void assureElasticsearchIsClean() {
//    try {
//      SearchResponse response = esClient
//        .prepareSearch()
//        .setQuery(matchAllQuery())
//        .get();
//      Long hits = response.getHits().getTotalHits();
//      assertThat("Elasticsearch was expected to be clean!", hits, is(0L));
//    } catch (IndexNotFoundException e) {
////      nothing to do
//    }
//  }

  public void disableCleanup() {
    this.haveToClean = false;
  }


  public void persistOperateEntitiesNew(List<? extends OperateEntity> operateEntities) throws PersistenceException {
    try {
      BulkRequestBuilder bulkRequest = esClient.prepareBulk();
      for (OperateEntity entity : operateEntities) {
        final String alias = getEntityToESAliasMap().get(entity.getClass());
        final IndexRequestBuilder indexRequestBuilder =
          esClient
            .prepareIndex(alias, ElasticsearchUtil.ES_INDEX_TYPE, entity.getId())
            .setSource(objectMapper.writeValueAsString(entity), XContentType.JSON);
        if (entity instanceof ActivityInstanceForListViewEntity) {
          indexRequestBuilder.setRouting(((ActivityInstanceForListViewEntity)entity).getWorkflowInstanceId());
        }
        bulkRequest.add(indexRequestBuilder);
      }
      ElasticsearchUtil.processBulkRequest(bulkRequest, true);
    } catch (Exception ex) {
      throw new PersistenceException(ex);
    }

  }

  public Map<Class<? extends OperateEntity>, String> getEntityToESAliasMap(){
    if (entityToESAliasMap == null) {
      entityToESAliasMap = new HashMap<>();
      entityToESAliasMap.put(WorkflowInstanceForListViewEntity.class, listViewTemplate.getAlias());
      entityToESAliasMap.put(ActivityInstanceForListViewEntity.class, listViewTemplate.getAlias());
    }
    return entityToESAliasMap;
  }

}
