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
