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
