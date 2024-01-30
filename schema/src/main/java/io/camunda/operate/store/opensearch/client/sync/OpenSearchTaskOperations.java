/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.store.opensearch.client.sync;

import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.tasks.GetTasksResponse;
import org.opensearch.client.opensearch.tasks.Info;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;


public class OpenSearchTaskOperations extends OpenSearchRetryOperation {
  public OpenSearchTaskOperations(Logger logger, OpenSearchClient openSearchClient) {
    super(logger, openSearchClient);
  }

  private static  String defaultTaskErrorMessage(String id) {
    return String.format("Failed to fetch task %s", id);
  }

  public GetTasksResponse task(String id) {
    return safe(() -> super.task(id), e -> defaultTaskErrorMessage(id));
  }

  public Map<String, Info> tasksWithActions(List<String> actions){
    return safe(() -> super.tasksWithActions(actions), e -> defaultTaskErrorMessage(String.format("Failed to fetch tasksWithActions for actions %s", actions)));
  }
}
