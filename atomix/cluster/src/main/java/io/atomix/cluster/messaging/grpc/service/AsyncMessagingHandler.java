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

import com.google.protobuf.ByteString;
import io.atomix.utils.net.Address;
import io.camunda.zeebe.messaging.protocol.MessagingOuterClass.Response;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;

public final class AsyncMessagingHandler extends RequestHandler<Response> {
  private final BiFunction<Address, byte[], CompletableFuture<byte[]>> handler;

  public AsyncMessagingHandler(
      final String type,
      final Executor executor,
      final BiFunction<Address, byte[], CompletableFuture<byte[]>> handler) {
    super(type, executor);
    this.handler = handler;
  }

  @Override
  protected void handle(
      final Address replyTo,
      final byte[] payload,
      final StreamObserver<Response> responseObserver) {
    final var result = handler.apply(replyTo, payload);

    if (responseObserver instanceof ServerCallStreamObserver) {
      final var serverObserver = (ServerCallStreamObserver<Response>) responseObserver;
      serverObserver.setOnCancelHandler(() -> result.cancel(false));
    }

    result.whenCompleteAsync(
        (response, error) -> handleResponse(responseObserver, response, error), executor);
  }

  private void handleResponse(
      final StreamObserver<Response> responseObserver,
      final byte[] payload,
      final Throwable error) {
    if (error != null) {
      responseObserver.onError(mapErrorToStatus(error));
      return;
    }

    final var response = Response.newBuilder().setPayload(ByteString.copyFrom(payload)).build();
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }
}
