/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.dao.opensearch;

import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.webapp.opensearch.OpensearchQueryDSLWrapper;
import io.camunda.operate.webapp.opensearch.OpensearchRequestDSLWrapper;

public abstract class OpensearchDao {
  protected final OpensearchQueryDSLWrapper queryDSLWrapper;
  protected final OpensearchRequestDSLWrapper requestDSLWrapper;
  protected final RichOpenSearchClient richOpenSearchClient;
  protected OperateProperties operateProperties;

  protected OpensearchDao(OpensearchQueryDSLWrapper queryDSLWrapper, OpensearchRequestDSLWrapper requestDSLWrapper,
                       RichOpenSearchClient richOpenSearchClient, OperateProperties operateProperties) {
    this.queryDSLWrapper = queryDSLWrapper;
    this.requestDSLWrapper = requestDSLWrapper;
    this.richOpenSearchClient = richOpenSearchClient;
    this.operateProperties = operateProperties;
  }
}
