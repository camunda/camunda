/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves the current node's task id from the ECS task metadata endpoint (v4), if the process is
 * running as an ECS task. See <a
 * href="https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task-metadata-endpoint-v4.html">...</a>.
 *
 * <p>The endpoint is eventually consistent during task startup: it can refuse connections, return a
 * 5xx, or return a 200 with the {@code TaskARN} field not yet populated. AWS guidance is to treat
 * these as transient and retry with bounded exponential backoff. A 404 is not transient (wrong
 * launch type or agent version) so we stop immediately.
 */
final class ECSTaskIdResolver {
  private static final Logger LOG = LoggerFactory.getLogger(ECSTaskIdResolver.class);
  private static final long RETRY_BUDGET_MILLIS = 15_000;
  private static final long INITIAL_BACKOFF_MILLIS = 100;
  private static final long MAX_BACKOFF_MILLIS = 2_000;

  private ECSTaskIdResolver() {}

  static Optional<String> resolve(final boolean resolveTaskId) {
    return resolveTaskId ? resolve() : Optional.empty();
  }

  static Optional<String> resolve(final boolean resolveTaskId, final String metadataUri) {
    return resolveTaskId ? resolve(metadataUri) : Optional.empty();
  }

  static Optional<String> resolve() {
    final var metadataUri = System.getenv("ECS_CONTAINER_METADATA_URI_V4");
    if (metadataUri == null || metadataUri.isBlank()) {
      LOG.info("Expected env var ECS_CONTAINER_METADATA_URI_V4 to be defined, but it wasn't");
      return Optional.empty();
    }
    return resolve(metadataUri);
  }

  static Optional<String> resolve(final String metadataUri) {
    return resolve(metadataUri, RETRY_BUDGET_MILLIS, INITIAL_BACKOFF_MILLIS);
  }

  static Optional<String> resolve(
      final String metadataUri, final long retryBudgetMillis, final long initialBackoffMillis) {
    final long deadlineMillis = System.currentTimeMillis() + retryBudgetMillis;
    long backoffMillis = initialBackoffMillis;
    while (true) {
      final var attempt = tryResolve(metadataUri);
      if (attempt.taskId().isPresent() || !attempt.retryable()) {
        return attempt.taskId();
      }
      final long remaining = deadlineMillis - System.currentTimeMillis();
      if (remaining <= 0) {
        LOG.warn("Gave up fetching ECS task ID after {}ms", retryBudgetMillis);
        return Optional.empty();
      }
      try {
        Thread.sleep(Math.min(backoffMillis, remaining));
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        return Optional.empty();
      }
      backoffMillis = Math.min(backoffMillis * 2, MAX_BACKOFF_MILLIS);
    }
  }

  private static AttemptResult tryResolve(final String metadataUri) {
    HttpURLConnection connection = null;
    try {
      connection = (HttpURLConnection) URI.create(metadataUri + "/task").toURL().openConnection();
      connection.setConnectTimeout(2_000);
      connection.setReadTimeout(2_000);
      final int status = connection.getResponseCode();
      if (status == HttpURLConnection.HTTP_NOT_FOUND) {
        LOG.warn(
            "ECS metadata endpoint returned 404; likely an unsupported launch type or agent version");
        return AttemptResult.giveUp();
      }
      if (status >= 500) {
        LOG.debug("ECS metadata endpoint returned {}, will retry", status);
        return AttemptResult.retry();
      }
      if (status != HttpURLConnection.HTTP_OK) {
        LOG.warn("ECS metadata endpoint returned unexpected status {}", status);
        return AttemptResult.giveUp();
      }
      try (final var in = connection.getInputStream()) {
        final var taskId =
            new ObjectMapper()
                .readTree(in)
                .path("TaskARN")
                .asOptional()
                .map(JsonNode::asText)
                .map(taskArn -> taskArn.substring(taskArn.lastIndexOf('/') + 1));
        if (taskId.isEmpty()) {
          LOG.debug("ECS metadata response missing TaskARN, will retry");
          return AttemptResult.retry();
        }
        return new AttemptResult(taskId, false);
      }
    } catch (final IOException | RuntimeException e) {
      LOG.debug("Failed to reach ECS metadata endpoint, will retry", e);
      return AttemptResult.retry();
    } finally {
      if (connection != null) {
        connection.disconnect();
      }
    }
  }

  private record AttemptResult(Optional<String> taskId, boolean retryable) {
    static AttemptResult retry() {
      return new AttemptResult(Optional.empty(), true);
    }

    static AttemptResult giveUp() {
      return new AttemptResult(Optional.empty(), false);
    }
  }
}
