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

/**
 * @author Svetlana Dorokhova.
 */
@Component
@Profile("elasticsearch")
public class ElasticsearchBulkProcessor extends Thread {

  private Logger logger = LoggerFactory.getLogger(ElasticsearchBulkProcessor.class);

  @Autowired
  private TransportClient esClient;

  @Autowired
  private Map<Class<? extends OperateEntity>, ElasticsearchRequestCreator> esRequestCreatorsMap;

  public void persistOperateEntities(List<? extends OperateEntity> entitiesToPersist) {

    try {
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
    } catch (Exception ex) {
      logger.error("Error while persisting entities", ex);
      //TODO
    }

  }

  protected void processBulkRequest(BulkRequestBuilder bulkRequest) throws InterruptedException, java.util.concurrent.ExecutionException {
    final BulkResponse bulkItemResponses = bulkRequest.execute().get();
    final BulkItemResponse[] items = bulkItemResponses.getItems();
    for (BulkItemResponse responseItem: items) {
      if (responseItem.isFailed()) {
        logger.error(String.format("%s failed for type [%s] and id [%s]: %s", responseItem.getOpType(), responseItem.getType(), responseItem.getId(),
          responseItem.getFailureMessage()), responseItem.getFailure().getCause());
        throw new RuntimeException("Operation failed: " + responseItem.getFailureMessage(), responseItem.getFailure().getCause());     //TODO
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
        final int batchSize = operateProperties.getElasticsearch().getInsertBatchSize();
        int entitiesCount = 0;
        for (String topicName : operateProperties.getZeebe().getTopics()) {
          List<OperateEntity> entitiesToPersist = new ArrayList<>();
          entityStorage.getOperateEntititesQueue(topicName).drainTo(entitiesToPersist, batchSize);
          if (entitiesToPersist.size() > 0) {
            entitiesCount += entitiesToPersist.size();
            persistOperateEntities(entitiesToPersist);
          }

        }

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
    start();
  }

}
