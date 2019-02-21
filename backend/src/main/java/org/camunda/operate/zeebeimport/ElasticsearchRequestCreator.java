/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport;

import org.camunda.operate.entities.OperateEntity;
import org.camunda.operate.exceptions.PersistenceException;
import org.elasticsearch.action.bulk.BulkRequestBuilder;


@FunctionalInterface
public interface ElasticsearchRequestCreator<T extends OperateEntity> {

  BulkRequestBuilder addRequestToBulkQuery(BulkRequestBuilder bulkRequestBuilder, T entity) throws PersistenceException;

}
