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
