/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.externalcode.client.sync;

import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import java.util.List;
import java.util.Map;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.tasks.GetTasksResponse;
import org.opensearch.client.opensearch.tasks.Info;

public class OpenSearchTaskOperations extends OpenSearchRetryOperation {
  public OpenSearchTaskOperations(
      OpenSearchClient openSearchClient, final OptimizeIndexNameService indexNameService) {
    super(openSearchClient, indexNameService);
  }

  private static String defaultTaskErrorMessage(String id) {
    return String.format("Failed to fetch task %s", id);
  }

  @Override
  public GetTasksResponse task(String id) {
    return safe(() -> super.task(id), e -> defaultTaskErrorMessage(id));
  }

  @Override
  public Map<String, Info> tasksWithActions(List<String> actions) {
    return safe(
        () -> super.tasksWithActions(actions),
        e ->
            defaultTaskErrorMessage(
                String.format("Failed to fetch tasksWithActions for actions %s", actions)));
  }
}
