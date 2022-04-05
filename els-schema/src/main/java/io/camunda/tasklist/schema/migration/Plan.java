/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.schema.migration;

import io.camunda.tasklist.es.RetryElasticsearchClient;
import io.camunda.tasklist.exceptions.MigrationException;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/** A plan consists of executable Steps. The plan can be execute on an elasticsearch client. */
public interface Plan {

  static ReindexPlan forReindex() {
    return new ReindexPlan();
  }

  default List<Step> getSteps() {
    return Collections.emptyList();
  }

  void executeOn(final RetryElasticsearchClient retryElasticsearchClient)
      throws IOException, MigrationException;
}
