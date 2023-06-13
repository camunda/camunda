/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.store.elasticsearch;

import io.camunda.operate.store.ZeebeStore;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!opensearch")
public class ElasticsearchZeebeStore implements ZeebeStore {

  private static final Logger logger = LoggerFactory.getLogger(ZeebeStore.class);

  @Autowired
  @Qualifier("zeebeEsClient")
  private RestHighLevelClient zeebeEsClient;

  @Override
  public void refreshIndex(String indexPattern) {
    RefreshRequest refreshRequest = new RefreshRequest(indexPattern);
    try {
      RefreshResponse refresh = zeebeEsClient.indices().refresh(refreshRequest, RequestOptions.DEFAULT);
      if (refresh.getFailedShards() > 0) {
        logger.warn("Unable to refresh indices: {}", indexPattern);
      }
    } catch (Exception ex) {
      logger.warn(String.format("Unable to refresh indices: %s", indexPattern), ex);
    }
  }
}
