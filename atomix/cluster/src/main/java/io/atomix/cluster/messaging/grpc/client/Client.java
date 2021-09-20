package io.atomix.cluster.messaging.grpc.client;

import io.camunda.zeebe.messaging.protocol.MessagingGrpc.MessagingStub;
import io.camunda.zeebe.util.CloseableSilently;
import io.grpc.ManagedChannel;

final class Client implements CloseableSilently {
  private final ManagedChannel channel;
  private final MessagingStub stub;

  Client(final ManagedChannel channel, final MessagingStub stub) {
    this.channel = channel;
    this.stub = stub;
  }

  @Override
  public void close() {
    channel.shutdownNow();
  }

  public ManagedChannel getChannel() {
    return channel;
  }

  public MessagingStub getStub() {
    return stub;
  }
}
