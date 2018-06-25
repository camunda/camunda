package org.camunda.operate.es.writer;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.camunda.operate.entities.OperateEntity;
import org.camunda.operate.property.OperateProperties;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;


@Component
public class ElasticsearchBulkProcessor extends Thread {

  private Logger logger = LoggerFactory.getLogger(ElasticsearchBulkProcessor.class);

  @Autowired
  private TransportClient esClient;

  @Autowired
  private Map<Class<? extends OperateEntity>, ElasticsearchRequestCreator> esRequestCreatorsMap;

  public void persistOperateEntities(List<? extends OperateEntity> entitiesToPersist) throws PersistenceException {

      logger.debug("Writing [{}] entities to elasticsearch", entitiesToPersist.size());
      BulkRequestBuilder bulkRequest = esClient.prepareBulk();
      for (OperateEntity operateEntity : entitiesToPersist) {
        final ElasticsearchRequestCreator esRequestCreator = esRequestCreatorsMap.get(operateEntity.getClass());
        if (esRequestCreator == null) {
          logger.warn("Unable to persist entity of type [{}]", operateEntity.getClass());
        } else {
          bulkRequest = esRequestCreator.addRequestToBulkQuery(bulkRequest, operateEntity);
        }
      }
      processBulkRequest(bulkRequest);


  }

  protected void processBulkRequest(BulkRequestBuilder bulkRequest) throws PersistenceException {
    if (bulkRequest.request().requests().size() > 0) {
      try {
        final BulkResponse bulkItemResponses = bulkRequest.execute().get();
        final BulkItemResponse[] items = bulkItemResponses.getItems();
        for (BulkItemResponse responseItem : items) {
          if (responseItem.isFailed()) {
            logger.error(String.format("%s failed for type [%s] and id [%s]: %s", responseItem.getOpType(), responseItem.getType(), responseItem.getId(),
              responseItem.getFailureMessage()), responseItem.getFailure().getCause());
            throw new PersistenceException("Operation failed: " + responseItem.getFailureMessage(), responseItem.getFailure().getCause(), responseItem.getItemId());
          }
        }
      } catch (InterruptedException | java.util.concurrent.ExecutionException ex) {
        throw new PersistenceException("Error when persisting the entities to Elasticsearch: " + ex.getMessage(), ex);
      }
    }
  }

  @Autowired
  private EntityStorage entityStorage;

  @Autowired
  private OperateProperties operateProperties;

  @Override
  public void run() {
    while (true) {
      try {

        int entitiesCount = processNextEntitiesBatch();

        //TODO we can implement backoff strategy, if there is not enough data
        if (entitiesCount == 0) {
          try {
            Thread.sleep(1000);
          } catch (InterruptedException e) {

          }
        }
      } catch (Exception ex) {
        //retry
      }

    }
  }


  @PostConstruct
  public void init() {
    if (operateProperties.isStartLoadingDataOnStartup()) {
      start();
    }
  }


  public synchronized int processNextEntitiesBatch() {
    final int batchSize = operateProperties.getElasticsearch().getInsertBatchSize();
    int entitiesCount = 0;

    for (String topicName : operateProperties.getZeebe().getTopics()) {
      List<OperateEntity> entitiesToPersist = new ArrayList<>();
      entityStorage.getOperateEntititesQueue(topicName).drainTo(entitiesToPersist, batchSize);
      if (entitiesToPersist.size() > 0) {
        entitiesCount += entitiesToPersist.size();
        try {
          persistOperateEntities(entitiesToPersist);
        } catch (PersistenceException ex) {
          logger.error("Error occurred while persisting the entities to Elasticsearch. Retrying...", ex);
          //try once again and skip
          try {
            persistOperateEntities(entitiesToPersist);
          } catch (PersistenceException ex2) {
            final OperateEntity failingEntity = entitiesToPersist.get(ex2.getFailingRequestId());
            //TODO what to do if the 2nd attempt failed again OPE-38
            logger.error("Error occurred while persisting the entities to Elasticsearch. Skipping.", ex2);
          }
        }
      }

    }
    return entitiesCount;
  }

}
