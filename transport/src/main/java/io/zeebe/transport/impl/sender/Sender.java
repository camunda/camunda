/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.transport.impl.sender;

import io.zeebe.transport.ClientResponse;
import io.zeebe.transport.Loggers;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.impl.ControlMessages;
import io.zeebe.transport.impl.IncomingResponse;
import io.zeebe.transport.impl.RemoteAddressImpl;
import io.zeebe.transport.impl.TransportChannel;
import io.zeebe.transport.impl.actor.ActorContext;
import io.zeebe.transport.impl.memory.TransportMemoryPool;
import io.zeebe.util.ByteValue;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.channel.ConcurrentQueueChannel;
import io.zeebe.util.sched.clock.ActorClock;
import io.zeebe.util.sched.future.ActorFuture;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.agrona.DeadlineTimerWheel;
import org.agrona.DeadlineTimerWheel.TimerHandler;
import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.ManyToOneConcurrentLinkedQueue;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;

public class Sender extends Actor implements TimerHandler {
  private static final int MAX_REQUEST_CONSUME_BATCH_SIZE = 100;

  private static final int DEFAULT_BATCH_SIZE = (int) ByteValue.ofKilobytes(128).toBytes();

  private static final Logger LOG = Loggers.TRANSPORT_LOGGER;
  protected final Duration keepAlivePeriod;
  private final ConcurrentQueueChannel<IncomingResponse> submittedResponses =
      new ConcurrentQueueChannel<>(new ManyToOneConcurrentLinkedQueue<>());
  private final ConcurrentQueueChannel<OutgoingRequest> submittedRequests =
      new ConcurrentQueueChannel<>(new ManyToOneConcurrentLinkedQueue<>());
  private final ConcurrentQueueChannel<OutgoingMessage> submittedMessages =
      new ConcurrentQueueChannel<>(new ManyToOneConcurrentLinkedQueue<>());

  private final Long2ObjectHashMap<OutgoingRequest> inFlightRequests = new Long2ObjectHashMap<>();
  private final Long2ObjectHashMap<OutgoingRequest> requestsByTimeoutIds =
      new Long2ObjectHashMap<>();

  private final Int2ObjectHashMap<ChannelWriteQueue> channelMap = new Int2ObjectHashMap<>();
  private final List<ChannelWriteQueue> channelList = new ArrayList<>();

  private final Deque<Batch> recycledBuffers = new LinkedList<>();
  private final Runnable sendNext = this::sendNext;
  private final TransportMemoryPool messageMemoryPool;
  private final TransportMemoryPool requestMemoryPool;
  private long nextRequestId = 0;
  private DeadlineTimerWheel requestTimeouts;

  public Sender(
      ActorContext actorContext,
      TransportMemoryPool messageMemoryPool,
      TransportMemoryPool requestMemoryPool,
      Duration keepalivePeriod) {
    this.messageMemoryPool = messageMemoryPool;
    this.requestMemoryPool = requestMemoryPool;
    this.keepAlivePeriod = keepalivePeriod;

    actorContext.setSender(this);
  }

  @Override
  protected void onActorStarted() {
    requestTimeouts =
        new DeadlineTimerWheel(TimeUnit.MILLISECONDS, ActorClock.currentTimeMillis(), 1, 32);

    actor.consume(submittedMessages, this::processSubmittedMessages);
    actor.consume(submittedRequests, this::processSubmittedRequests);
    actor.consume(submittedResponses, this::processIncomingResponses);

    actor.runAtFixedRate(Duration.ofMillis(100), this::processTimeouts);

    if (keepAlivePeriod != null) {
      actor.runAtFixedRate(keepAlivePeriod, this::sendKeepalives);
    }
  }

  private void processTimeouts() {
    final long now = ActorClock.currentTimeMillis();

    while (requestTimeouts.poll(now, this, Integer.MAX_VALUE) > 0) {
      // process timeouts
    }
  }

  private void processIncomingResponses() {
    while (!submittedResponses.isEmpty()) {
      final IncomingResponse response = submittedResponses.poll();

      if (response != null) {
        onResponseReceived(response);
      }
    }

    sendNext();
  }

  private void processSubmittedRequests() {
    for (int i = 0; i < MAX_REQUEST_CONSUME_BATCH_SIZE && !submittedRequests.isEmpty(); i++) {
      final OutgoingRequest request = submittedRequests.poll();

      if (request != null) {
        LOG.trace("New request submitted");
        onRequestSubmitted(request);
      }
    }

    sendNext();
  }

  private void processSubmittedMessages() {
    while (!submittedMessages.isEmpty()) {
      final OutgoingMessage message = submittedMessages.poll();

      if (message != null) {
        LOG.trace("New message submitted");
        onMessageSubmitted(message);
      }
    }

    sendNext();
  }

  private void onResponseReceived(final IncomingResponse response) {
    final OutgoingRequest request = inFlightRequests.remove(response.getRequestId());

    if (request != null) {
      boolean shouldRetry = false;

      try {
        shouldRetry = !request.tryComplete(response);
      } catch (Exception e) {
        request.fail(e);
        reclaimRequestBuffer(request.getRequestBuffer().byteBuffer());
        return;
      }

      if (shouldRetry) {
        // retry after delay
        actor.runDelayed(Duration.ofMillis(1), () -> submittedRequests.offer(request));
      } else {
        reclaimRequestBuffer(request.getRequestBuffer().byteBuffer());

        final long timerId = request.getTimerId();

        if (timerId != -1) {
          requestTimeouts.cancelTimer(timerId);
          requestsByTimeoutIds.remove(timerId);
        }
      }
    }
  }

  private void onRequestSubmitted(final OutgoingRequest request) {
    if (!request.hasTimeoutScheduled()) {
      final long timerId =
          requestTimeouts.scheduleTimer(
              ActorClock.currentTimeMillis() + request.getTimeout().toMillis());
      request.setTimerId(timerId);
      requestsByTimeoutIds.put(timerId, request);
    }

    if (!request.isTimedout()) {
      final RemoteAddress remoteAddress = request.getNextRemoteAddress();

      if (remoteAddress != null) {
        final ChannelWriteQueue sendQueue = channelMap.get(remoteAddress.getStreamId());
        if (sendQueue != null) {
          request.markRemoteAddress(remoteAddress);
          sendQueue.offer(request);
        } else {
          // channel not open, retry
          actor.runDelayed(Duration.ofMillis(10), () -> submittedRequests.offer(request));
        }
      } else {
        // no remote address available, retry
        actor.runDelayed(Duration.ofMillis(10), () -> submittedRequests.offer(request));
      }
    }
  }

  private void onMessageSubmitted(final OutgoingMessage message) {
    final int remoteStreamId = message.getRemoteStreamId();
    final ChannelWriteQueue sendQueue = channelMap.get(remoteStreamId);
    if (sendQueue != null) {
      try {
        sendQueue.offer(message);
      } finally {
        reclaimMessageBuffer(message.getAllocatedBuffer());
      }
    } else if (ActorClock.currentTimeMillis() < message.getDeadline()) {
      // channel not open, retry
      actor.runDelayed(Duration.ofMillis(10), () -> submittedMessages.offer(message));
    } else {
      LOG.trace("Drop message because the channel is not open.");
      reclaimMessageBuffer(message.getAllocatedBuffer());
    }
  }

  private void sendNext() {
    boolean hasPending = false;

    for (int i = 0; i < channelList.size(); i++) {
      final ChannelWriteQueue channelSendQueue = channelList.get(i);

      channelSendQueue.write();

      hasPending |= channelSendQueue.hasPending();
    }

    if (hasPending) {
      actor.submit(sendNext);
    }
  }

  private void sendKeepalives() {
    for (ChannelWriteQueue channelWriteQueue : channelList) {
      if (!channelWriteQueue.hasPending()) {
        channelWriteQueue
            .getPendingWrites()
            .addLast(new ControlMessage(ControlMessages.KEEP_ALIVE));
      }
    }

    sendNext();
  }

  public ActorFuture<ClientResponse> submitRequest(OutgoingRequest request) {
    submittedRequests.add(request);
    return request.getResponseFuture();
  }

  public void submitMessage(OutgoingMessage outgoingMessage) {
    submittedMessages.add(outgoingMessage);
  }

  public void submitResponse(IncomingResponse incomingClientResponse) {
    submittedResponses.add(incomingClientResponse);
  }

  public ActorFuture<Void> onChannelConnected(TransportChannel ch) {
    return actor.call(
        () -> {
          final ChannelWriteQueue sendQueue = new ChannelWriteQueue(ch);
          channelMap.put(ch.getStreamId(), sendQueue);
          channelList.add(sendQueue);
        });
  }

  public ActorFuture<Void> onChannelClosed(TransportChannel channel) {
    return actor.call(
        () -> {
          final ChannelWriteQueue sendQueue = channelMap.remove(channel.getStreamId());
          if (sendQueue != null) {
            channelList.remove(sendQueue);
            // re-submit pending requests so that they can be retried
            sendQueue.pendingWrites.forEach(Batch::onChannelClosed);
          }
        });
  }

  @Override
  public boolean onTimerExpiry(TimeUnit timeUnit, long now, long timerId) {
    final OutgoingRequest request = requestsByTimeoutIds.get(timerId);

    if (request != null) {
      reclaimRequestBuffer(request.getRequestBuffer().byteBuffer());
      request.timeout();
      inFlightRequests.remove(request.getLastRequestId());
    }

    return true;
  }

  public ByteBuffer allocateMessageBuffer(int length) {
    return messageMemoryPool.allocate(length);
  }

  public void reclaimMessageBuffer(ByteBuffer allocatedBuffer) {
    messageMemoryPool.reclaim(allocatedBuffer);
  }

  public ByteBuffer allocateRequestBuffer(int requestedCapacity) {
    return requestMemoryPool.allocate(requestedCapacity);
  }

  public void reclaimRequestBuffer(ByteBuffer allocatedBuffer) {
    requestMemoryPool.reclaim(allocatedBuffer);
  }

  public void failPendingRequestsToRemote(RemoteAddressImpl remoteAddress, String reason) {}

  public class ChannelWriteQueue {
    private final Deque<Batch> pendingWrites = new LinkedList<>();

    private final TransportChannel channel;

    private Batch currentWrite;

    public ChannelWriteQueue(TransportChannel channel) {
      this.channel = channel;
    }

    public boolean hasPending() {
      return currentWrite != null || !pendingWrites.isEmpty();
    }

    public void write() {
      if (hasPending()) {
        if (currentWrite == null) {
          currentWrite = pendingWrites.poll();
          currentWrite.prepareWrite();
        }

        currentWrite.writeTo(channel);

        if (!currentWrite.hasRemaining()) {
          currentWrite.recycle();
          currentWrite = null;
        }
      }
    }

    public void offer(OutgoingRequest request) {
      // try to fit into last pending batch
      final Batch existingBatch = pendingWrites.peekLast();

      if (existingBatch == null || !existingBatch.addToBatch(request, channel)) {
        // try to recycle existing batch
        final Iterator<Batch> recycledBuffersIterator = recycledBuffers.iterator();
        while (recycledBuffersIterator.hasNext()) {
          final Batch batch = recycledBuffersIterator.next();

          if (batch.addToBatch(request, channel)) {
            recycledBuffersIterator.remove();
            pendingWrites.addLast(batch);
            return;
          }
        }

        // allocate new batch
        final Batch batch =
            new Batch(Math.max(DEFAULT_BATCH_SIZE, request.getRequestBuffer().capacity()));
        batch.addToBatch(request, channel);
        pendingWrites.addLast(batch);
      }
    }

    public void offer(OutgoingMessage message) {
      // try to fit into last pending batch
      Batch batch = pendingWrites.peekLast();

      if (batch == null || !batch.addToBatch(message)) {
        boolean hasRecycled = false;

        // try to recycle existing batch
        final Iterator<Batch> recycledBuffersIterator = recycledBuffers.iterator();
        while (recycledBuffersIterator.hasNext()) {
          batch = recycledBuffersIterator.next();

          if (batch.addToBatch(message)) {
            recycledBuffersIterator.remove();
            hasRecycled = true;
            break;
          }
        }

        if (!hasRecycled) {
          // allocate new batch
          batch = new Batch(Math.max(DEFAULT_BATCH_SIZE, message.getBuffer().capacity()));
          batch.addToBatch(message);
        }

        pendingWrites.addLast(batch);
      }
    }

    public Deque<Batch> getPendingWrites() {
      return pendingWrites;
    }
  }

  class ControlMessage extends Batch {
    ControlMessage(DirectBuffer controlMessageTemplate) {
      super(controlMessageTemplate.capacity());
      view.putBytes(0, controlMessageTemplate, 0, controlMessageTemplate.capacity());
      this.writeOffset = controlMessageTemplate.capacity();
    }

    @Override
    public void recycle() {
      // don't do it
    }
  }

  private class Batch {
    final List<OutgoingRequest> requestsInBatch = new ArrayList<>();

    final UnsafeBuffer view = new UnsafeBuffer();
    final ByteBuffer batchBuffer;
    int writeOffset = 0;

    Batch(int size) {
      batchBuffer = ByteBuffer.allocateDirect(size);
      view.wrap(batchBuffer);
    }

    public boolean addToBatch(OutgoingRequest request, TransportChannel channel) {
      final DirectBuffer requestBuffer = request.getRequestBuffer();
      final int requestLength = requestBuffer.capacity();

      if (writeOffset + requestLength <= batchBuffer.capacity()) {
        final long requestId = ++nextRequestId;

        request.setLastRequestId(requestId);

        request.getHeaderWriter().setStreamId(channel.getStreamId()).setRequestId(requestId);

        requestBuffer.getBytes(0, batchBuffer, writeOffset, requestLength);
        writeOffset += requestLength;
        requestsInBatch.add(request);

        inFlightRequests.put(requestId, request);

        return true;
      } else {
        return false;
      }
    }

    public boolean addToBatch(OutgoingMessage message) {
      final DirectBuffer buffer = message.getBuffer();
      final int requiredLength = buffer.capacity();

      if (writeOffset + requiredLength <= batchBuffer.capacity()) {
        buffer.getBytes(0, batchBuffer, writeOffset, requiredLength);
        writeOffset += requiredLength;

        return true;
      } else {
        return false;
      }
    }

    public boolean writeTo(TransportChannel channel) {
      return channel.write(batchBuffer) > 0;
    }

    public void prepareWrite() {
      batchBuffer.position(0);
      batchBuffer.limit(writeOffset);
    }

    public boolean hasRemaining() {
      return batchBuffer.hasRemaining();
    }

    public void recycle() {
      writeOffset = 0;
      view.setMemory(0, view.capacity(), (byte) 0);
      requestsInBatch.clear();
      recycledBuffers.push(this);
    }

    public void onChannelClosed() {
      requestsInBatch.forEach(Sender.this::submitRequest);
      recycle();
    }
  }
}
