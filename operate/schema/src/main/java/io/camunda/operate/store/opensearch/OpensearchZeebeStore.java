/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store.opensearch;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.config.operate.OperateProperties;
import io.camunda.operate.store.ZeebeStore;
import java.io.IOException;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchZeebeStore implements ZeebeStore {

  private static final Logger LOGGER = LoggerFactory.getLogger(OpensearchZeebeStore.class);

  @Autowired
  @Qualifier("zeebeOpensearchClient")
  private OpenSearchClient openSearchClient;

  @Autowired private OperateProperties operateProperties;

  @Override
  public void refreshIndex(final String indexPattern) {
    try {
      final var response = openSearchClient.indices().refresh(r -> r.index(indexPattern));
      if (!response.shards().failures().isEmpty()) {
        LOGGER.warn("Unable to refresh indices: {}", indexPattern);
      }
    } catch (final Exception ex) {
      LOGGER.warn(String.format("Unable to refresh indices: %s", indexPattern), ex);
    }
  }

  @Override
  public boolean zeebeIndicesExists(final String indexPattern) {
    try {
      final var exists =
          openSearchClient
              .indices()
              .exists(r -> r.index(indexPattern).allowNoIndices(false).ignoreUnavailable(true))
              .value();
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
    return operateProperties.getZeebeOpensearch().getPrefix();
  }
}
