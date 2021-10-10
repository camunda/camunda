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
package io.atomix.cluster.messaging.grpc;

import io.grpc.Status.Code;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.net.ConnectException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

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
    if (!(t instanceof StatusRuntimeException)) {
      future.completeExceptionally(t);
      return;
    }

    // convert to switch/case as the number of cases grow; this should anyway go away as we migrate
    // things to use the gRPC services over time
    final var statusError = (StatusRuntimeException) t;
    if (statusError.getStatus().getCode() == Code.DEADLINE_EXCEEDED) {
      future.completeExceptionally(new TimeoutException(statusError.getMessage()));
    } else if (statusError.getStatus().getCode() == Code.UNAVAILABLE) {
      future.completeExceptionally(new ConnectException(statusError.getMessage()));
    } else {
      future.completeExceptionally(t);
    }
  }

  @Override
  public void onCompleted() {
    if (!future.isDone()) {
      future.completeExceptionally(
          new IllegalStateException("Call completed unexpectedly without receiving a response"));
    }
  }
}
