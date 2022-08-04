/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.transport;

import io.camunda.zeebe.broker.transport.AsyncApiRequestHandler.RequestReader;
import io.camunda.zeebe.broker.transport.AsyncApiRequestHandler.ResponseWriter;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.util.Either;

/**
 * @param <R>
 * @param <W>
 */
public abstract class ApiRequestHandler<R extends RequestReader<?>, W extends ResponseWriter>
    extends AsyncApiRequestHandler<R, W> {

  protected ApiRequestHandler(final R requestReader, final W responseWriter) {
    super(requestReader, responseWriter);
  }

  protected abstract Either<ErrorResponseWriter, W> handle(
      final int partitionId,
      final long requestId,
      final R requestReader,
      final W responseWriter,
      final ErrorResponseWriter errorWriter);

  @Override
  protected final ActorFuture<Either<ErrorResponseWriter, W>> handleAsync(
      final int partitionId,
      final long requestId,
      final R requestReader,
      final W responseWriter,
      final ErrorResponseWriter errorWriter) {
    return CompletableActorFuture.completed(
        handle(partitionId, requestId, requestReader, responseWriter, errorWriter));
  }
}
