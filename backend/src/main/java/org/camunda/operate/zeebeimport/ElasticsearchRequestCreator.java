package org.camunda.operate.zeebeimport;

import org.camunda.operate.entities.OperateEntity;
import org.camunda.operate.exceptions.PersistenceException;
import org.elasticsearch.action.bulk.BulkRequestBuilder;


@FunctionalInterface
public interface ElasticsearchRequestCreator<T extends OperateEntity> {

  BulkRequestBuilder addRequestToBulkQuery(BulkRequestBuilder bulkRequestBuilder, T entity) throws PersistenceException;

}
