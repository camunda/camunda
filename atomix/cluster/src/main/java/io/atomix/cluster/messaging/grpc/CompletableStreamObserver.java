package io.atomix.cluster.messaging.grpc;

import io.grpc.stub.StreamObserver;
import java.util.concurrent.CompletableFuture;

final class CompletableStreamObserver<V> implements StreamObserver<V> {
  private final CompletableFuture<V> future;

  CompletableStreamObserver(final CompletableFuture<V> future) {
    this.future = future;
  }

  @Override
  public void onNext(final V value) {
    future.complete(value);
  }

  @Override
  public void onError(final Throwable t) {
    future.completeExceptionally(t);
  }

  @Override
  public void onCompleted() {
    if (!future.isDone()) {
      future.completeExceptionally(
          new IllegalStateException("Call completed unexpectedly without receiving a response"));
    }
  }
}
