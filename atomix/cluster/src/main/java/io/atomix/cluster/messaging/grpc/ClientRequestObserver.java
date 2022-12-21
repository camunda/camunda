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

import io.atomix.cluster.messaging.MessagingException;
import io.atomix.utils.net.Address;
import io.camunda.zeebe.messaging.protocol.MessagingOuterClass.Request;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.net.ConnectException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

final class ClientRequestObserver<V> implements StreamObserver<V> {
  private final Address remoteAddress;
  private final Request request;
  private final CompletableFuture<V> future;

  ClientRequestObserver(
      final Address remoteAddress, final Request request, final CompletableFuture<V> future) {
    this.remoteAddress = remoteAddress;
    this.request = request;
    this.future = future;
  }

  @Override
  public void onNext(final V value) {
    future.complete(value);
  }

  @Override
  public void onError(final Throwable t) {
    if (!(t instanceof final StatusRuntimeException statusError)) {
      future.completeExceptionally(t);
      return;
    }

    // convert to switch/case as the number of cases grow; this should anyway go away as we migrate
    // things to use the gRPC services over time
    switch (statusError.getStatus().getCode()) {
      case DEADLINE_EXCEEDED -> future.completeExceptionally(
          new TimeoutException(
              String.format(
                  "Request %s to %s timed out: %s", formatRequest(), remoteAddress, statusError)));
      case UNIMPLEMENTED -> future.completeExceptionally(
          new MessagingException.NoRemoteHandler(
              String.format(
                  "Request %s to %s failed due to no remote handler registered",
                  formatRequest(), remoteAddress),
              statusError));
      case INTERNAL -> future.completeExceptionally(
          new MessagingException.RemoteHandlerFailure(
              String.format("Request %s to %s failed unexpectedly", formatRequest(), remoteAddress),
              statusError));
      case UNAVAILABLE -> future.completeExceptionally(
          new ConnectException(
              String.format(
                  "Failed to connect to %s for request %s: %s",
                  remoteAddress, formatRequest(), statusError.getMessage())));
      default -> future.completeExceptionally(t);
    }
  }

  @Override
  public void onCompleted() {
    if (!future.isDone()) {
      future.completeExceptionally(
          new IllegalStateException("Call completed unexpectedly without receiving a response"));
    }
  }

  private String formatRequest() {
    return String.format(
        "{type='%s', cluster='%s', replyTo='%s', payload=bytes[%d]}",
        request.getType(), request.getCluster(), request.getReplyTo(), request.getPayload().size());
  }
}
