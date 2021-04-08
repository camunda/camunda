/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.migrate33To34;

import org.camunda.optimize.service.es.schema.IndexMappingCreator;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.indices.GetIndexRequest;

import java.io.IOException;

public abstract class AbstractMigrateInstanceIndicesIT extends AbstractUpgrade33IT {
  protected boolean indexExists(final IndexMappingCreator index) {
    return indexExists(indexNameService.getOptimizeIndexNameWithVersion(index));
  }

  protected boolean indexExists(final String indexOrAliasName) {
    final GetIndexRequest request = new GetIndexRequest(indexOrAliasName);
    try {
      return prefixAwareClient.exists(request, RequestOptions.DEFAULT);
    } catch (IOException e) {
      final String message = String.format(
        "Could not check if [%s] index already exist.", String.join(",", indexOrAliasName)
      );
      throw new OptimizeRuntimeException(message, e);
    }
  }
}
