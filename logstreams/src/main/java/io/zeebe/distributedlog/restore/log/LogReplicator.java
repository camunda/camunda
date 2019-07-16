/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.distributedlog.restore.log;

import io.atomix.cluster.MemberId;
import io.zeebe.distributedlog.restore.RestoreClient;
import io.zeebe.distributedlog.restore.log.impl.DefaultLogReplicationRequest;
import io.zeebe.util.ZbLogger;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.slf4j.Logger;

public class LogReplicator {
  private final LogReplicationAppender appender;
  private final RestoreClient client;
  private final Executor executor;
  private final Logger logger;

  public LogReplicator(LogReplicationAppender appender, RestoreClient client, Executor executor) {
    this(appender, client, executor, new ZbLogger(LogReplicator.class));
  }

  public LogReplicator(
      LogReplicationAppender appender, RestoreClient client, Executor executor, Logger logger) {
    this.appender = appender;
    this.client = client;
    this.executor = executor;
    this.logger = logger;
  }

  public CompletableFuture<Long> replicate(MemberId server, long from, long to) {
    return replicate(server, from, to, false);
  }

  public CompletableFuture<Long> replicate(
      MemberId server, long from, long to, boolean includeFromPosition) {
    final CompletableFuture<Long> result = new CompletableFuture<>();
    replicateInternal(server, from, to, includeFromPosition, result);
    return result;
  }

  private void replicateInternal(
      MemberId server,
      long from,
      long to,
      boolean includeFromPosition,
      CompletableFuture<Long> result) {
    final LogReplicationRequest request =
        new DefaultLogReplicationRequest(from, to, includeFromPosition);
    client
        .requestLogReplication(server, request)
        .whenCompleteAsync(
            (r, e) -> {
              if (e != null) {
                logger.debug("Error replicating {} from {}", request, server, e);
                result.completeExceptionally(e);
              } else {
                if (!r.isValid()) {
                  logger.debug(
                      "Received invalid response {} when requesting {} from {}",
                      r,
                      request,
                      server);
                  result.completeExceptionally(
                      new InvalidLogReplicationResponse(server, request, r));
                  return;
                }

                if (appendEvents(server, from, to, result, r)) {
                  if (r.getToPosition() < to && r.hasMoreAvailable()) {
                    replicateInternal(server, r.getToPosition(), to, false, result);
                  } else {
                    result.complete(r.getToPosition());
                  }
                }
              }
            },
            executor);
  }

  private boolean appendEvents(
      MemberId server,
      long from,
      long to,
      CompletableFuture<Long> result,
      LogReplicationResponse response) {
    try {
      final long appendResult =
          appender.append(response.getToPosition(), response.getSerializedEvents());
      if (appendResult <= 0) {
        logger.debug("Failed to append events from {} - {} with result {}", from, to, appendResult);
        result.completeExceptionally(new FailedAppendException(server, from, to, appendResult));
      }
    } catch (RuntimeException error) {
      logger.debug("Error when appending events from {} - {}", from, to, error);
      result.completeExceptionally(error);
      return false;
    }

    return true;
  }
}
