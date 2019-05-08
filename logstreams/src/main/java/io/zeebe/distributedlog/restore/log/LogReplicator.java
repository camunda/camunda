/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.distributedlog.restore.log;

import io.atomix.cluster.MemberId;
import io.zeebe.distributedlog.restore.log.impl.DefaultLogReplicationRequest;
import io.zeebe.util.ZbLogger;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.slf4j.Logger;

public class LogReplicator {
  private final LogReplicationAppender appender;
  private final LogReplicationClient client;
  private final Executor executor;
  private final Logger logger;

  public LogReplicator(
      LogReplicationAppender appender, LogReplicationClient client, Executor executor) {
    this(appender, client, executor, new ZbLogger(LogReplicator.class));
  }

  public LogReplicator(
      LogReplicationAppender appender,
      LogReplicationClient client,
      Executor executor,
      Logger logger) {
    this.appender = appender;
    this.client = client;
    this.executor = executor;
    this.logger = logger;
  }

  public CompletableFuture<Long> replicate(MemberId server, long from, long to) {
    final CompletableFuture<Long> result = new CompletableFuture<>();
    replicateInternal(server, from, to, result);
    return result;
  }

  private void replicateInternal(
      MemberId server, long from, long to, CompletableFuture<Long> result) {
    final LogReplicationRequest request = new DefaultLogReplicationRequest(from, to);
    client
        .replicate(server, request)
        .whenCompleteAsync(
            (r, e) -> {
              if (e != null) {
                logger.trace("Error replicating {} from {}", request, server, e);
                result.completeExceptionally(e);
              } else {
                if (!r.isValid()) {
                  logger.trace(
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
                    replicateInternal(server, r.getToPosition(), to, result);
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
        logger.trace("Failed to append events from {} - {} with result {}", from, to, appendResult);
        result.completeExceptionally(new FailedAppendException(server, from, to, appendResult));
      }
    } catch (RuntimeException error) {
      logger.trace("Error when appending events from {} - {}", from, to, error);
      result.completeExceptionally(error);
      return false;
    }

    return true;
  }
}
