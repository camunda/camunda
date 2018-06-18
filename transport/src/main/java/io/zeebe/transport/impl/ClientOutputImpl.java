/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
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
package io.zeebe.transport.impl;

import io.zeebe.transport.*;
import io.zeebe.transport.impl.sender.*;
import io.zeebe.util.buffer.BufferWriter;
import io.zeebe.util.sched.future.ActorFuture;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class ClientOutputImpl implements ClientOutput {
  protected final Sender requestManager;
  protected final Duration defaultRequestRetryTimeout;

  public ClientOutputImpl(Sender requestManager, Duration defaultRequestRetryTimeout) {
    this.requestManager = requestManager;
    this.defaultRequestRetryTimeout = defaultRequestRetryTimeout;
  }

  @Override
  public boolean sendMessage(TransportMessage transportMessage) {
    final BufferWriter writer = transportMessage.getWriter();
    final int framedMessageLength =
        TransportHeaderWriter.getFramedMessageLength(writer.getLength());

    final ByteBuffer allocatedBuffer = requestManager.allocateMessageBuffer(framedMessageLength);

    if (allocatedBuffer != null) {
      try {
        final int remoteStreamId = transportMessage.getRemoteStreamId();
        final UnsafeBuffer bufferView = new UnsafeBuffer(allocatedBuffer);
        final TransportHeaderWriter headerWriter = new TransportHeaderWriter();

        headerWriter.wrapMessage(bufferView, writer, remoteStreamId);

        final OutgoingMessage outgoingMessage = new OutgoingMessage(remoteStreamId, bufferView);

        requestManager.submitMessage(outgoingMessage);

        return true;
      } catch (RuntimeException e) {
        requestManager.reclaimMessageBuffer(allocatedBuffer);
        throw e;
      }
    } else {
      return false;
    }
  }

  @Override
  public ActorFuture<ClientResponse> sendRequest(RemoteAddress addr, BufferWriter writer) {
    return sendRequest(addr, writer, defaultRequestRetryTimeout);
  }

  @Override
  public ActorFuture<ClientResponse> sendRequest(
      RemoteAddress addr, BufferWriter writer, Duration timeout) {
    return sendRequestWithRetry(() -> addr, (b) -> false, writer, timeout);
  }

  @Override
  public ActorFuture<ClientResponse> sendRequestWithRetry(
      Supplier<RemoteAddress> remoteAddressSupplier,
      Predicate<DirectBuffer> responseInspector,
      BufferWriter writer,
      Duration timeout) {
    final int messageLength = writer.getLength();
    final int framedLength = TransportHeaderWriter.getFramedRequestLength(messageLength);

    final ByteBuffer allocatedBuffer = requestManager.allocateRequestBuffer(framedLength);

    if (allocatedBuffer != null) {
      try {
        final UnsafeBuffer bufferView = new UnsafeBuffer(allocatedBuffer);
        final OutgoingRequest request =
            new OutgoingRequest(remoteAddressSupplier, responseInspector, bufferView, timeout);

        request.getHeaderWriter().wrapRequest(bufferView, writer);

        return requestManager.submitRequest(request);
      } catch (RuntimeException e) {
        requestManager.reclaimRequestBuffer(allocatedBuffer);
        throw e;
      }
    } else {
      return null;
    }
  }
}
