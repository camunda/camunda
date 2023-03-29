/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.schema.migration;

import io.camunda.operate.es.RetryElasticsearchClient;
import io.camunda.operate.exceptions.MigrationException;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
/**
 * A plan consists of executable Steps.
 * The plan can be execute on an elasticsearch client.
 */
public interface Plan {

  String PRESERVE_INDEX_SUFFIX_SCRIPT = "ctx._index = params.dstIndex+'_' + (ctx._index.substring(ctx._index.indexOf('_') + 1, ctx._index.length()));";

  static ReindexPlan forReindex() {
    return new ReindexPlan();
  }

  static ReindexWithQueryAndScriptPlan forReindexWithQueryAndScriptPlan() {
    return new ReindexWithQueryAndScriptPlan();
  }

  default List<Step> getSteps() {
    return Collections.emptyList();
  }

  void executeOn(final RetryElasticsearchClient retryElasticsearchClient) throws IOException, MigrationException;

  default void validateMigrationResults(final RetryElasticsearchClient retryElasticsearchClient)
      throws MigrationException {
  }

}
