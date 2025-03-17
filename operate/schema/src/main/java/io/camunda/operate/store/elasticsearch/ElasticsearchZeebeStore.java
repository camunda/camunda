/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store.elasticsearch;

import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.config.operate.OperateProperties;
import io.camunda.operate.store.ZeebeStore;
import java.io.IOException;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshResponse;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class ElasticsearchZeebeStore implements ZeebeStore {

  private static final Logger LOGGER = LoggerFactory.getLogger(ZeebeStore.class);

  @Autowired
  @Qualifier("zeebeEsClient")
  private RestHighLevelClient zeebeEsClient;

  @Autowired private OperateProperties operateProperties;

  @Override
  public void refreshIndex(final String indexPattern) {
    final RefreshRequest refreshRequest = new RefreshRequest(indexPattern);
    try {
      final RefreshResponse refresh =
          zeebeEsClient.indices().refresh(refreshRequest, RequestOptions.DEFAULT);
      if (refresh.getFailedShards() > 0) {
        LOGGER.warn("Unable to refresh indices: {}", indexPattern);
      }
    } catch (final Exception ex) {
      LOGGER.warn(String.format("Unable to refresh indices: %s", indexPattern), ex);
    }
  }

  @Override
  public boolean zeebeIndicesExists(final String indexPattern) {
    try {
      final GetIndexRequest request = new GetIndexRequest(indexPattern);
      request.indicesOptions(IndicesOptions.fromOptions(true, false, true, false));
      final boolean exists = zeebeEsClient.indices().exists(request, RequestOptions.DEFAULT);
      if (exists) {
        LOGGER.debug("Data already exists in Zeebe.");
      }
      return exists;
    } catch (final IOException io) {
      LOGGER.debug(
          "Error occurred while checking existence of data in Zeebe: {}. Demo data won't be created.",
          io.getMessage());
      return false;
    }
  }

  @Override
  public String getZeebeIndexPrefix() {
    return operateProperties.getZeebeElasticsearch().getPrefix();
  }
}
