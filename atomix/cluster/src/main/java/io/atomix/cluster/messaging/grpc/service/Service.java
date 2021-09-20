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
