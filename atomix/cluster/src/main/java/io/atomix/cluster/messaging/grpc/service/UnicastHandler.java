package io.atomix.cluster.messaging.grpc.service;

import io.atomix.utils.net.Address;
import io.camunda.zeebe.messaging.protocol.MessagingOuterClass.EmptyResponse;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;

public class UnicastHandler extends RequestHandler<EmptyResponse> {
  private BiConsumer<Address, byte[]> handler;

  public UnicastHandler(
      final String type, final Executor executor, final BiConsumer<Address, byte[]> handler) {
    super(type, executor);
    this.handler = handler;
  }

  @Override
  protected void handle(
      final Address replyTo,
      final byte[] payload,
      final StreamObserver<EmptyResponse> responseObserver) {
    handler.accept(replyTo, payload);
  }
}
