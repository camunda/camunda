/*
 * Copyright 2017-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
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
package io.atomix.primitive.session.impl;

import com.google.common.base.Throwables;
import io.atomix.primitive.PrimitiveException;
import io.atomix.primitive.PrimitiveState;
import io.atomix.primitive.operation.PrimitiveOperation;
import io.atomix.primitive.session.SessionClient;
import io.atomix.utils.concurrent.Futures;
import io.atomix.utils.concurrent.Scheduler;
import io.atomix.utils.logging.ContextualLoggerFactory;
import io.atomix.utils.logging.LoggerContext;
import java.net.ConnectException;
import java.nio.channels.ClosedChannelException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import org.slf4j.Logger;

/** Retrying primitive client. */
public class RetryingSessionClient extends DelegatingSessionClient {
  private final Logger log;
  private final SessionClient session;
  private final Scheduler scheduler;
  private final int maxRetries;
  private final Duration delayBetweenRetries;

  private final Predicate<Throwable> retryableCheck =
      e ->
          e instanceof ConnectException
              || e instanceof TimeoutException
              || e instanceof ClosedChannelException
              || e instanceof PrimitiveException.Unavailable
              || e instanceof PrimitiveException.Timeout
              || e instanceof PrimitiveException.QueryFailure
              || e instanceof PrimitiveException.UnknownClient
              || e instanceof PrimitiveException.UnknownSession
              || e instanceof PrimitiveException.ClosedSession;

  public RetryingSessionClient(
      final SessionClient session,
      final Scheduler scheduler,
      final int maxRetries,
      final Duration delayBetweenRetries) {
    super(session);
    this.session = session;
    this.scheduler = scheduler;
    this.maxRetries = maxRetries;
    this.delayBetweenRetries = delayBetweenRetries;
    this.log =
        ContextualLoggerFactory.getLogger(
            getClass(),
            LoggerContext.builder(SessionClient.class)
                .addValue(this.session.sessionId())
                .add("type", this.session.type())
                .add("name", this.session.name())
                .build());
  }

  @Override
  public CompletableFuture<byte[]> execute(final PrimitiveOperation operation) {
    if (getState() == PrimitiveState.CLOSED) {
      return Futures.exceptionalFuture(new PrimitiveException.ClosedSession());
    }
    final CompletableFuture<byte[]> future = new CompletableFuture<>();
    execute(operation, 1, future);
    return future;
  }

  private void execute(
      final PrimitiveOperation operation,
      final int attemptIndex,
      final CompletableFuture<byte[]> future) {
    session
        .execute(operation)
        .whenComplete(
            (r, e) -> {
              if (e != null) {
                if (attemptIndex < maxRetries + 1
                    && retryableCheck.test(Throwables.getRootCause(e))) {
                  log.debug(
                      "Retry attempt ({} of {}). Failure due to {}",
                      attemptIndex,
                      maxRetries,
                      Throwables.getRootCause(e).getClass());
                  scheduler.schedule(
                      delayBetweenRetries.multipliedBy(2 ^ attemptIndex),
                      () -> execute(operation, attemptIndex + 1, future));
                } else {
                  future.completeExceptionally(e);
                }
              } else {
                future.complete(r);
              }
            });
  }
}
