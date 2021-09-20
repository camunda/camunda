package io.atomix.cluster.messaging.grpc.service;

import com.google.protobuf.ByteString;
import io.atomix.utils.net.Address;
import io.camunda.zeebe.messaging.protocol.MessagingOuterClass.Response;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;

public class SyncMessagingHandler extends RequestHandler<Response> {
  private final BiFunction<Address, byte[], byte[]> handler;

  public SyncMessagingHandler(
      final String type,
      final Executor executor,
      final BiFunction<Address, byte[], byte[]> handler) {
    super(type, executor);
    this.handler = handler;
  }

  @Override
  protected void handle(
      final Address replyTo, final byte[] payload, final StreamObserver<Response> responseObserver) {
    final var responsePayload = handler.apply(replyTo, payload);
    final var response =
        Response.newBuilder().setPayload(ByteString.copyFrom(responsePayload)).build();
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }
}
