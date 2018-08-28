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

import io.zeebe.transport.ServerOutput;
import io.zeebe.transport.ServerResponse;
import io.zeebe.transport.impl.sender.OutgoingMessage;
import io.zeebe.transport.impl.sender.Sender;
import io.zeebe.transport.impl.sender.TransportHeaderWriter;
import io.zeebe.util.buffer.BufferWriter;
import java.nio.ByteBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class ServerOutputImpl implements ServerOutput {
  private static final long NO_RETRIES = 0;

  private final Sender sender;

  public ServerOutputImpl(Sender sender) {
    this.sender = sender;
  }

  @Override
  public boolean sendMessage(int remoteStreamId, BufferWriter writer) {
    final int framedMessageLength =
        TransportHeaderWriter.getFramedMessageLength(writer.getLength());

    final ByteBuffer allocatedBuffer = sender.allocateMessageBuffer(framedMessageLength);

    if (allocatedBuffer != null) {
      try {
        final UnsafeBuffer bufferView = new UnsafeBuffer(allocatedBuffer);
        final TransportHeaderWriter headerWriter = new TransportHeaderWriter();
        headerWriter.wrapMessage(bufferView, writer, remoteStreamId);

        final OutgoingMessage outgoingMessage =
            new OutgoingMessage(remoteStreamId, bufferView, NO_RETRIES);

        sender.submitMessage(outgoingMessage);

        return true;
      } catch (RuntimeException e) {
        sender.reclaimMessageBuffer(allocatedBuffer);
        throw e;
      }
    } else {
      return false;
    }
  }

  @Override
  public boolean sendResponse(ServerResponse response) {
    final BufferWriter writer = response.getWriter();
    final int framedLength = TransportHeaderWriter.getFramedRequestLength(writer.getLength());

    final ByteBuffer allocatedBuffer = sender.allocateMessageBuffer(framedLength);

    if (allocatedBuffer != null) {
      try {
        final int remoteStreamId = response.getRemoteStreamId();
        final long requestId = response.getRequestId();

        final UnsafeBuffer bufferView = new UnsafeBuffer(allocatedBuffer);
        final TransportHeaderWriter headerWriter = new TransportHeaderWriter();

        headerWriter.wrapRequest(bufferView, writer);

        headerWriter.setStreamId(remoteStreamId).setRequestId(requestId);

        final OutgoingMessage outgoingMessage =
            new OutgoingMessage(remoteStreamId, bufferView, NO_RETRIES);

        sender.submitMessage(outgoingMessage);

        return true;
      } catch (RuntimeException e) {
        sender.reclaimMessageBuffer(allocatedBuffer);
        throw e;
      }
    } else {
      return false;
    }
  }
}
