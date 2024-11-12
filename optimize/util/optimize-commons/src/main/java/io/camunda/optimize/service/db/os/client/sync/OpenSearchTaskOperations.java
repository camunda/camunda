/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.client.sync;

import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.tasks.GetTasksResponse;
import org.opensearch.client.opensearch.tasks.Info;

public class OpenSearchTaskOperations extends OpenSearchRetryOperation {

  public OpenSearchTaskOperations(
      final OpenSearchClient openSearchClient, final OptimizeIndexNameService indexNameService) {
    super(openSearchClient, indexNameService);
  }

  private static String defaultTaskErrorMessage(final String id) {
    return String.format("Failed to fetch task %s", id);
  }

  @Override
  public GetTasksResponse task(final String id) {
    return safe(() -> super.task(id), e -> defaultTaskErrorMessage(id));
  }

  @Override
  public Map<String, Info> tasksWithActions(final List<String> actions) {
    return safe(
        () -> super.tasksWithActions(actions),
        e ->
            defaultTaskErrorMessage(
                String.format("Failed to fetch tasksWithActions for actions %s", actions)));
  }

  public GetTasksResponse taskWithRetries(final String id) {
    return executeWithGivenRetries(
        1,
        "Get task information for " + id,
        () -> {
          try {
            return super.task(id);
          } catch (final ConnectionClosedException e) {
            System.out.println("Failed will retry for Task ID " + id);
            return null;
          }
        },
        Objects::isNull);
  }
}
