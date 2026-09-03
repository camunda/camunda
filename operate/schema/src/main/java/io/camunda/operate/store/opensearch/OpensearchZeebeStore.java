/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store.opensearch;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.store.ZeebeStore;
import java.io.IOException;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.indices.RefreshResponse;
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
  public void refreshIndex(String indexPattern) {
    final RefreshResponse response;
    try {
      response = openSearchClient.indices().refresh(r -> r.index(indexPattern));
    } catch (Exception ex) {
      throw new OperateRuntimeException(
          String.format("Unable to refresh indices matching pattern %s", indexPattern), ex);
    }
    if (response.shards().total().intValue() == 0) {
      throw new OperateRuntimeException("Refresh matched no indices for pattern " + indexPattern);
    }
    if (!response.shards().failures().isEmpty()) {
      throw new OperateRuntimeException(
          String.format(
              "Unable to refresh indices matching pattern %s: %d of %d shards failed",
              indexPattern,
              response.shards().failures().size(),
              response.shards().total().intValue()));
    }
  }

  @Override
  public String getZeebeIndexPrefix() {
    return operateProperties.getZeebeOpensearch().getPrefix();
  }

  @Override
  public boolean zeebeIndicesExists(String indexPattern) {
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
    } catch (IOException io) {
      LOGGER.debug(
          "Error occurred while checking existence of data in Zeebe: {}. Demo data won't be created.",
          io.getMessage());
      return false;
    }
  }
}
