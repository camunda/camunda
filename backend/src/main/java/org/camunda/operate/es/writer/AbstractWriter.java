package org.camunda.operate.es.writer;

import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Svetlana Dorokhova.
 */
public class AbstractWriter {

  private final Logger logger = LoggerFactory.getLogger(AbstractWriter.class);

  protected void processBulkRequest(BulkRequestBuilder bulkRequest) throws InterruptedException, java.util.concurrent.ExecutionException {
    final BulkResponse bulkItemResponses = bulkRequest.execute().get();
    final BulkItemResponse[] items = bulkItemResponses.getItems();
    for (BulkItemResponse responseItem: items) {
      if (responseItem.isFailed()) {
        logger.error("Insert failed: " + responseItem.getFailureMessage());
        throw new RuntimeException("Insert failed: " + responseItem.getFailureMessage());     //TODO
      }
    }
  }

}
