package io.atomix.cluster.messaging.grpc;

import com.google.common.util.concurrent.MoreExecutors;
import com.google.protobuf.ByteString;
import io.atomix.cluster.messaging.MessagingService;
import io.atomix.cluster.messaging.UnicastService;
import io.atomix.cluster.messaging.grpc.client.ClientRegistry;
import io.atomix.cluster.messaging.grpc.service.ServiceHandlerRegistry;
import io.atomix.utils.net.Address;
import io.camunda.zeebe.messaging.protocol.MessagingOuterClass.EmptyResponse;
import io.camunda.zeebe.messaging.protocol.MessagingOuterClass.Request;
import io.camunda.zeebe.messaging.protocol.MessagingOuterClass.Response;
import io.camunda.zeebe.util.CloseableSilently;
import io.netty.util.NetUtil;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

final class GrpcMessagingService implements MessagingService, UnicastService, CloseableSilently {
  private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

  private final Address advertisedAddress;
  private final String clusterId;
  private final List<Address> bindAddresses;
  private final ServiceHandlerRegistry handlerRegistry;
  private final ClientRegistry clientRegistry;

  private final String serverReplyTo;

  GrpcMessagingService(
      final Address advertisedAddress,
      final String clusterId,
      final List<Address> bindAddresses,
      final ServiceHandlerRegistry handlerRegistry,
      final ClientRegistry clientRegistry) {
    this.advertisedAddress = advertisedAddress;
    this.clusterId = clusterId;
    this.bindAddresses = bindAddresses;
    this.handlerRegistry = handlerRegistry;
    this.clientRegistry = clientRegistry;

    serverReplyTo = NetUtil.toSocketAddressString(advertisedAddress.socketAddress());
  }

  @Override
  public void close() {
    clientRegistry.close();
    handlerRegistry.clear();
  }

  @Override
  public Address address() {
    return advertisedAddress;
  }

  @Override
  public Collection<Address> bindingAddresses() {
    return bindAddresses;
  }

  @Override
  public CompletableFuture<Void> sendAsync(
      final Address address, final String type, final byte[] payload, final boolean keepAlive) {
    final CompletableFuture<Response> result = new CompletableFuture<>();
    final var request = createRequest(type, payload);
    final var client = clientRegistry.get(address);
    client
        .withDeadlineAfter(DEFAULT_TIMEOUT.toNanos(), TimeUnit.NANOSECONDS)
        .send(request, new CompletableStreamObserver<>(result));

    return result.thenApply(ok -> null);
  }

  @Override
  public CompletableFuture<byte[]> sendAndReceive(
      final Address address, final String type, final byte[] payload, final boolean keepAlive) {
    return sendAndReceive(address, type, payload, keepAlive, DEFAULT_TIMEOUT);
  }

  @Override
  public CompletableFuture<byte[]> sendAndReceive(
      final Address address,
      final String type,
      final byte[] payload,
      final boolean keepAlive,
      final Executor executor) {
    // we don't use the executor specific call in production, and to be honest, consumers who care
    // about it should use specify the executor in their completion stage's callback, e.g. using
    // whenCompleteAsync, or thenApplyAsync, etc.
    return sendAndReceive(address, type, payload, keepAlive, DEFAULT_TIMEOUT, executor);
  }

  @Override
  public CompletableFuture<byte[]> sendAndReceive(
      final Address address,
      final String type,
      final byte[] payload,
      final boolean keepAlive,
      final Duration timeout) {
    return sendAndReceive(
        address, type, payload, keepAlive, timeout, MoreExecutors.directExecutor());
  }

  @Override
  public CompletableFuture<byte[]> sendAndReceive(
      final Address address,
      final String type,
      final byte[] payload,
      final boolean keepAlive,
      final Duration timeout,
      final Executor executor) {
    final CompletableFuture<Response> result = new CompletableFuture<>();
    final var request = createRequest(type, payload);

    final var client = clientRegistry.get(address);
    client
        .withDeadlineAfter(timeout.toNanos(), TimeUnit.NANOSECONDS)
        .sendAndReceive(request, new CompletableStreamObserver<>(result));

    return result.thenApplyAsync(response -> response.getPayload().toByteArray(), executor);
  }

  @Override
  public void registerHandler(
      final String type, final BiConsumer<Address, byte[]> handler, final Executor executor) {
    handlerRegistry.addMessagingHandler(type, handler, executor);
  }

  @Override
  public void registerHandler(
      final String type,
      final BiFunction<Address, byte[], byte[]> handler,
      final Executor executor) {
    handlerRegistry.addMessagingHandler(type, handler, executor);
  }

  @Override
  public void registerHandler(
      final String type, final BiFunction<Address, byte[], CompletableFuture<byte[]>> handler) {
    handlerRegistry.addMessagingHandler(type, handler);
  }

  @Override
  public void unregisterHandler(final String type) {
    handlerRegistry.removeMessagingHandler(type);
  }

  @Override
  public boolean isRunning() {
    // TODO: move this out of the interface and leave it with the Managed portion
    return true;
  }

  private Request createRequest(final String type, final byte[] payload) {
    return Request.newBuilder()
        .setCluster(clusterId)
        .setType(type)
        .setReplyTo(serverReplyTo)
        .setPayload(ByteString.copyFrom(payload))
        .build();
  }

  @Override
  public void unicast(final Address address, final String subject, final byte[] message) {
    final CompletableFuture<EmptyResponse> result = new CompletableFuture<>();
    final var request = createRequest(subject, message);
    final var client = clientRegistry.get(address);
    client.unicast(request, new CompletableStreamObserver<>(result));
  }

  @Override
  public void addListener(
      final String subject, final BiConsumer<Address, byte[]> listener, final Executor executor) {
    handlerRegistry.addUnicastHandler(subject, listener, executor);
  }

  @Override
  public void removeListener(final String subject, final BiConsumer<Address, byte[]> listener) {
    handlerRegistry.removeUnicastHandler(subject);
  }
}
