/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.store.opensearch;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.store.ZeebeStore;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchZeebeStore implements ZeebeStore {

  private static final Logger logger = LoggerFactory.getLogger(OpensearchZeebeStore.class);

  @Autowired
  @Qualifier("zeebeOpensearchClient")
  private OpenSearchClient openSearchClient;

  @Override
  public void refreshIndex(String indexPattern) {
    try {
      var response = openSearchClient.indices().refresh( r -> r.index(indexPattern));
      if (!response.shards().failures().isEmpty()) {
        logger.warn("Unable to refresh indices: {}", indexPattern);
      }
    } catch (Exception ex) {
      logger.warn(String.format("Unable to refresh indices: %s", indexPattern), ex);
    }
  }

  @Override
  public boolean zeebeIndicesExists(String indexPattern) {
    try{
      var exists = openSearchClient.indices().exists( r -> r
        .index(indexPattern)
        .allowNoIndices(false).ignoreUnavailable(true)).value();
      if (exists) {
        logger.debug("Data already exists in Zeebe.");
      }
      return exists;
    } catch (IOException io) {
      logger.debug("Error occurred while checking existence of data in Zeebe: {}. Demo data won't be created.", io.getMessage());
      return false;
    }
  }
}
