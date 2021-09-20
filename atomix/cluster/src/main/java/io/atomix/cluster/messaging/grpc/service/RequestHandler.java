/*
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
package io.atomix.cluster.messaging.grpc.service;

import io.atomix.utils.net.Address;
import io.camunda.zeebe.messaging.protocol.MessagingOuterClass.Request;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class RequestHandler<V> {
  private static final Logger LOGGER = LoggerFactory.getLogger(RequestHandler.class);

  protected final String type;
  protected final Executor executor;

  protected RequestHandler(final String type, final Executor executor) {
    this.type = Objects.requireNonNull(type);
    this.executor = Objects.requireNonNull(executor);
  }

  public void handle(final Request request, final StreamObserver<V> responseObserver) {
    final var replyTo = Address.from(request.getReplyTo());
    final var payload = request.getPayload().toByteArray();

    if (responseObserver instanceof ServerCallStreamObserver) {
      final var serverObserver = (ServerCallStreamObserver<V>) responseObserver;
      serverObserver.setOnCancelHandler(
          () -> LOGGER.trace("Request {} cancelled by the client", request));
    }

    executor.execute(
        () -> {
          try {
            handle(replyTo, payload, responseObserver);
          } catch (final Exception e) {
            LOGGER.debug("Unexpected error handling unicast request {}", request, e);
          }
        });
  }

  protected abstract void handle(
      final Address replyTo, final byte[] payload, StreamObserver<V> responseObserver);

  protected StatusRuntimeException mapErrorToStatus(final Throwable error) {
    if (error instanceof StatusRuntimeException) {
      return (StatusRuntimeException) error;
    } else if (error instanceof StatusException) {
      return ((StatusException) error).getStatus().asRuntimeException();
    } else if (error instanceof CompletionException || error instanceof ExecutionException) {
      return mapErrorToStatus(error.getCause());
    } else if (error instanceof TimeoutException) {
      return Status.DEADLINE_EXCEEDED.withCause(error).asRuntimeException();
    } else if (error instanceof CancellationException) {
      return Status.CANCELLED.withCause(error).asRuntimeException();
    } else {
      return Status.INTERNAL.withCause(error).asRuntimeException();
    }
  }
}
