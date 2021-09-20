package io.atomix.cluster.messaging.grpc.service;

import io.camunda.zeebe.messaging.protocol.MessagingOuterClass.Response;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.atomic.AtomicBoolean;

final class ResponseIgnoringObserver implements StreamObserver<Response> {
  private final StreamObserver<Response> delegate;
  private final AtomicBoolean isClosed = new AtomicBoolean();

  ResponseIgnoringObserver(final StreamObserver<Response> delegate) {
    this.delegate = delegate;
  }

  @Override
  public void onNext(final Response value) {
    delegate.onNext(Response.getDefaultInstance());
  }

  @Override
  public void onError(final Throwable t) {
    if (isClosed.compareAndSet(false, true)) {
      delegate.onError(t);
    }
  }

  @Override
  public void onCompleted() {
    if (isClosed.compareAndSet(false, true)) {
      delegate.onCompleted();
    }
  }
}
