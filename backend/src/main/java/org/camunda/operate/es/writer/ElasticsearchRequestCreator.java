package org.camunda.operate.es.writer;

import org.camunda.operate.entities.OperateEntity;
import org.elasticsearch.action.bulk.BulkRequestBuilder;

/**
 * @author Svetlana Dorokhova.
 */
@FunctionalInterface
public interface ElasticsearchRequestCreator<T extends OperateEntity> {

  BulkRequestBuilder addRequestToBulkQuery(BulkRequestBuilder bulkRequestBuilder, T entity);

}
