/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.util;

import io.zeebe.transport.ClientOutput;
import io.zeebe.transport.ClientResponse;
import io.zeebe.transport.impl.ClientResponseImpl;
import io.zeebe.transport.impl.IncomingResponse;
import io.zeebe.util.buffer.BufferWriter;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import uk.co.real_logic.sbe.ir.generated.MessageHeaderDecoder;

public class BufferingClientOutput implements ClientOutput {
  private static final AtomicInteger ID_GEN = new AtomicInteger();

  private final Duration defaultTimeout;
  private final List<Request> sentRequests = new CopyOnWriteArrayList<>();

  public BufferingClientOutput(final Duration defaultTimeout) {
    this.defaultTimeout = defaultTimeout;
  }

  @Override
  public ActorFuture<ClientResponse> sendRequest(Integer nodeId, BufferWriter writer) {
    return sendRequest(nodeId, writer, defaultTimeout);
  }

  @Override
  public ActorFuture<ClientResponse> sendRequest(
      Integer nodeId, BufferWriter writer, Duration timeout) {
    return sendRequestWithRetry(() -> nodeId, b -> false, writer, timeout);
  }

  @Override
  public ActorFuture<ClientResponse> sendRequestWithRetry(
      Supplier<Integer> nodeIdSupplier,
      Predicate<DirectBuffer> responseInspector,
      BufferWriter writer,
      Duration timeout) {
    final Request request = new Request(nodeIdSupplier.get(), writer, timeout);
    sentRequests.add(request);
    return request.response;
  }

  @Override
  public boolean sendMessage(Integer nodeId, BufferWriter writer) {
    throw new UnsupportedOperationException("not yet implemented");
  }

  public List<Request> getSentRequests() {
    return sentRequests;
  }

  public Request getLastRequest() {
    return sentRequests.isEmpty() ? null : sentRequests.get(sentRequests.size() - 1);
  }

  public class Request {
    private final int requestId = ID_GEN.incrementAndGet();
    private final Integer destination;
    private final BufferWriter request;
    private final ExpandableArrayBuffer requestBuffer;
    private final CompletableActorFuture<ClientResponse> response;
    private final Duration timeout;
    private final int templateId;

    Request(final Integer destination, final BufferWriter writer, final Duration timeout) {
      this.request = writer;
      this.requestBuffer = new ExpandableArrayBuffer(writer.getLength());
      this.response = new CompletableActorFuture<>();
      this.destination = destination;
      this.timeout = timeout;

      writer.write(this.requestBuffer, 0);

      final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
      this.templateId = headerDecoder.wrap(this.requestBuffer, 0).templateId();
    }

    public Integer getDestination() {
      return destination;
    }

    public void respondWith(final BufferWriter writer) {
      this.response.complete(generateResponse(writer));
    }

    public void respondWith(final Throwable t) {
      this.response.completeExceptionally(t);
    }

    public BufferWriter getRequest() {
      return request;
    }

    public int getTemplateId() {
      return templateId;
    }

    private ClientResponse generateResponse(final BufferWriter writer) {
      final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer(writer.getLength());
      writer.write(buffer, 0);

      final IncomingResponse response = new IncomingResponse(requestId, buffer);
      return new ClientResponseImpl(response, null);
    }
  }
}
