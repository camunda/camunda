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

import io.camunda.zeebe.messaging.protocol.MessagingGrpc.MessagingImplBase;
import io.camunda.zeebe.messaging.protocol.MessagingOuterClass.EmptyResponse;
import io.camunda.zeebe.messaging.protocol.MessagingOuterClass.Request;
import io.camunda.zeebe.messaging.protocol.MessagingOuterClass.Response;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.util.Collection;

public final class Service extends MessagingImplBase {
  private final String clusterId;
  private final ServiceHandlerRegistry handlers;

  public Service(final String clusterId, final ServiceHandlerRegistry handlers) {
    this.clusterId = clusterId;
    this.handlers = handlers;
  }

  @Override
  public void unicast(final Request request, final StreamObserver<EmptyResponse> responseObserver) {
    if (!clusterId.equals(request.getCluster())) {
      failOnWrongCluster(request, responseObserver);
      return;
    }

    final Collection<RequestHandler<EmptyResponse>> requestHandlers =
        handlers.getUnicastHandlers(request.getType());
    if (requestHandlers.isEmpty()) {
      failWithNoHandler(request, responseObserver);
      return;
    }

    // if one handler fails, the first error is propagated, but not further errors will be
    for (final var handler : requestHandlers) {
      handler.handle(request, responseObserver);
    }

    responseObserver.onNext(EmptyResponse.getDefaultInstance());
    responseObserver.onCompleted();
  }

  @Override
  public void send(final Request request, final StreamObserver<Response> responseObserver) {
    if (!clusterId.equals(request.getCluster())) {
      failOnWrongCluster(request, responseObserver);
      return;
    }

    final RequestHandler<Response> handler = handlers.getMessagingHandler(request.getType());
    if (handler == null) {
      responseObserver.onNext(Response.getDefaultInstance());
      responseObserver.onCompleted();
      return;
    }

    handleMessagingRequest(request, new ResponseIgnoringObserver(responseObserver), handler);
  }

  @Override
  public void sendAndReceive(
      final Request request, final StreamObserver<Response> responseObserver) {
    if (!clusterId.equals(request.getCluster())) {
      failOnWrongCluster(request, responseObserver);
      return;
    }

    final RequestHandler<Response> handler = handlers.getMessagingHandler(request.getType());
    if (handler == null) {
      failWithNoHandler(request, responseObserver);
      return;
    }

    handleMessagingRequest(request, responseObserver, handler);
  }

  private void handleMessagingRequest(
      final Request request,
      final StreamObserver<Response> responseObserver,
      final RequestHandler<Response> handler) {
    try {
      handler.handle(request, responseObserver);
    } catch (final Exception e) {
      responseObserver.onError(e);
    }
  }

  private <V> void failOnWrongCluster(
      final Request request, final StreamObserver<V> responseObserver) {
    responseObserver.onError(
        Status.PERMISSION_DENIED
            .augmentDescription(
                String.format(
                    "Expected to handle requests for cluster %s, but received one for cluster %s",
                    clusterId, request.getCluster()))
            .asRuntimeException());
  }

  private <V> void failWithNoHandler(
      final Request request, final StreamObserver<V> responseObserver) {
    responseObserver.onError(
        Status.UNAVAILABLE
            .augmentDescription("No registered handler for request of type " + request.getType())
            .asRuntimeException());
  }
}
