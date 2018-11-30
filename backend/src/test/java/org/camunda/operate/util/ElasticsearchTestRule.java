package org.camunda.operate.util;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import org.camunda.operate.entities.OperateEntity;
import org.camunda.operate.es.ElasticsearchSchemaManager;
import org.camunda.operate.es.types.TypeMappingCreator;
import org.camunda.operate.exceptions.PersistenceException;
import org.camunda.operate.property.OperateElasticsearchProperties;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.zeebeimport.ElasticsearchBulkProcessor;
import org.camunda.operate.zeebeimport.ZeebeESImporter;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.index.IndexNotFoundException;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

public class ElasticsearchTestRule extends ExternalResource {

  private static final Logger logger = LoggerFactory.getLogger(ElasticsearchTestRule.class);

  @Autowired
  protected TransportClient esClient;

  @Autowired
  @Qualifier("zeebeEsClient")
  protected TransportClient zeebeEsClient;

  @Autowired
  private List<TypeMappingCreator> typeMappingCreators;

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  private ElasticsearchSchemaManager elasticsearchSchemaManager;

  @Autowired
  private ZeebeESImporter zeebeESImporter;

  @Autowired
  private ElasticsearchBulkProcessor elasticsearchBulkProcessor;

  private boolean haveToClean = true;

  private String workflowIndexName;
  private String workflowInstanceIndexName;
  private String eventIndexName;
  private String importPositionIndexName;

  @Override
  public void before() {
    final String indexSuffix = TestUtil.createRandomString(10);
    workflowIndexName = OperateElasticsearchProperties.WORKFLOW_INDEX_PATTERN + "_" + indexSuffix;
    workflowInstanceIndexName = OperateElasticsearchProperties.WORKFLOW_INSTANCE_INDEX_PATTERN + "_" + indexSuffix;
    eventIndexName = OperateElasticsearchProperties.EVENT_INDEX_PATTERN + "_" + indexSuffix;
    importPositionIndexName = OperateElasticsearchProperties.IMPORT_POSITION_INDEX_PATTERN + "_" + indexSuffix;
    operateProperties.getElasticsearch().setWorkflowIndexName(workflowIndexName);
    operateProperties.getElasticsearch().setWorkflowInstanceIndexName(workflowInstanceIndexName);
    operateProperties.getElasticsearch().setEventIndexName(eventIndexName);
    operateProperties.getElasticsearch().setImportPositionIndexName(importPositionIndexName);

    //make aliases differ from index names
    operateProperties.getElasticsearch().setWorkflowAlias(workflowIndexName + "_alias");
    operateProperties.getElasticsearch().setWorkflowInstanceAlias(workflowInstanceIndexName + "_alias");
    operateProperties.getElasticsearch().setEventAlias(eventIndexName + "_alias");
    operateProperties.getElasticsearch().setImportPositionAlias(importPositionIndexName + "_alias");

    elasticsearchSchemaManager.createIndices();

  }

  @Override
  public void after() {
    removeAllIndices();
    operateProperties.getElasticsearch().setWorkflowIndexName(OperateElasticsearchProperties.WORKFLOW_INDEX_PATTERN);
    operateProperties.getElasticsearch().setWorkflowInstanceIndexName(OperateElasticsearchProperties.WORKFLOW_INSTANCE_INDEX_PATTERN);
    operateProperties.getElasticsearch().setEventIndexName(OperateElasticsearchProperties.EVENT_INDEX_PATTERN);
    operateProperties.getElasticsearch().setImportPositionIndexName(OperateElasticsearchProperties.IMPORT_POSITION_INDEX_PATTERN);

    operateProperties.getElasticsearch().setWorkflowAlias(OperateElasticsearchProperties.WORKFLOW_INDEX_PATTERN);
    operateProperties.getElasticsearch().setWorkflowInstanceAlias(OperateElasticsearchProperties.WORKFLOW_INSTANCE_INDEX_PATTERN);
    operateProperties.getElasticsearch().setEventAlias(OperateElasticsearchProperties.EVENT_INDEX_PATTERN);
    operateProperties.getElasticsearch().setImportPositionAlias(OperateElasticsearchProperties.IMPORT_POSITION_INDEX_PATTERN);

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

}
