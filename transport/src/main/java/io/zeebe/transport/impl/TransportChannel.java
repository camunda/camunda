/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.transport.impl;

import io.zeebe.dispatcher.FragmentHandler;
import io.zeebe.dispatcher.impl.log.DataFrameDescriptor;
import io.zeebe.transport.Loggers;
import io.zeebe.util.ZbLogger;
import io.zeebe.util.allocation.AllocatedBuffer;
import io.zeebe.util.allocation.BufferAllocators;
import java.io.IOException;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import org.agrona.DirectBuffer;
import org.agrona.LangUtil;
import org.agrona.concurrent.UnsafeBuffer;

public class TransportChannel {
  private static final ZbLogger LOG = Loggers.TRANSPORT_LOGGER;

  private static final AtomicIntegerFieldUpdater<TransportChannel> STATE_FIELD =
      AtomicIntegerFieldUpdater.newUpdater(TransportChannel.class, "state");

  private static final int CLOSED = 1;
  private static final int CONNECTING = 2;
  private static final int CONNECTED = 3;
  private final RemoteAddressImpl remoteAddress;
  private final AllocatedBuffer allocatedBuffer;
  private final ByteBuffer channelReadBuffer;
  private final UnsafeBuffer channelReadBufferView;
  private final ChannelLifecycleListener listener;
  private final FragmentHandler readHandler;

  @SuppressWarnings("unused") // used through STATE_FIELD
  private volatile int state = CLOSED;

  private SocketChannel media;

  private int connectAttempt;

  private List<SelectionKey> registeredKeys = Collections.synchronizedList(new ArrayList<>());

  public TransportChannel(
      ChannelLifecycleListener listener,
      RemoteAddressImpl remoteAddress,
      int maxMessageSize,
      FragmentHandler readHandler) {
    this.listener = listener;
    this.remoteAddress = remoteAddress;
    this.readHandler = readHandler;
    this.allocatedBuffer = BufferAllocators.allocateDirect(2 * maxMessageSize);
    this.channelReadBuffer = allocatedBuffer.getRawBuffer();
    this.channelReadBufferView = new UnsafeBuffer(channelReadBuffer);
  }

  public TransportChannel(
      ChannelLifecycleListener listener,
      RemoteAddressImpl remoteAddress,
      int maxMessageSize,
      FragmentHandler readHandler,
      SocketChannel media) {
    this(listener, remoteAddress, maxMessageSize, readHandler);
    this.media = media;
    STATE_FIELD.set(this, CONNECTED);
  }

  public int receive() {
    int workCount = 0;

    final int received = mediaReceive(media, channelReadBuffer);

    LOG.trace("Received {} bytes on channel {}", received, this);

    if (received < 0) {
      doClose();
      return workCount;
    }

    final int available = channelReadBuffer.position();

    LOG.trace("Channel read buffer has {} bytes available", available);

    int remaining = available;
    int offset = 0;

    while (remaining >= DataFrameDescriptor.HEADER_LENGTH) {
      workCount += 1;

      final int framedLength =
          channelReadBufferView.getInt(DataFrameDescriptor.lengthOffset(offset));
      final int msgLength = DataFrameDescriptor.messageLength(framedLength);
      final int msgOffset = DataFrameDescriptor.messageOffset(offset);
      final int frameLength = DataFrameDescriptor.alignedLength(framedLength);

      if (remaining < frameLength) {
        break;
      } else {
        final boolean handled = handleMessage(channelReadBufferView, msgOffset, msgLength);

        if (handled) {
          LOG.trace("Handler has handled message of {} bytes", framedLength);

          remaining -= frameLength;
          offset += frameLength;
        } else {
          break;
        }
      }
    }

    if (offset > 0) {
      channelReadBuffer.limit(available);
      channelReadBuffer.position(offset);
      channelReadBuffer.compact();
    }

    return workCount;
  }

  private boolean handleMessage(DirectBuffer buffer, int msgOffset, int msgLength) {
    try {
      return readHandler.onFragment(buffer, msgOffset, msgLength, getStreamId(), false)
          != FragmentHandler.POSTPONE_FRAGMENT_RESULT;
    } catch (Exception e) {
      LOG.trace("Failed to handle message", e);
      return true;
    }
  }

  private int mediaReceive(SocketChannel media, ByteBuffer receiveBuffer) {
    int bytesReceived = -2;

    try {
      bytesReceived = media.read(receiveBuffer);
    } catch (IOException e) {
      doClose();
    }

    return bytesReceived;
  }

  public int write(ByteBuffer buffer) {
    int bytesWritten = -1;

    try {
      bytesWritten = media.write(buffer);
    } catch (IOException e) {
      doClose();
    }

    return bytesWritten;
  }

  public int getStreamId() {
    return remoteAddress.getStreamId();
  }

  public void registerSelector(Selector selector, int ops) {
    try {
      final SelectionKey key = media.register(selector, ops);
      key.attach(this);
      registeredKeys.add(key);
    } catch (ClosedChannelException e) {
      LangUtil.rethrowUnchecked(e);
    }
  }

  public void removeSelector(Selector selector) {
    final SelectionKey key = media.keyFor(selector);
    if (key != null) {
      key.cancel();
      registeredKeys.remove(key);
    }
  }

  public boolean beginConnect(int attempt) {
    if (STATE_FIELD.compareAndSet(this, CLOSED, CONNECTING)) {
      connectAttempt = attempt;
      try {
        media = SocketChannel.open();
        media.setOption(StandardSocketOptions.TCP_NODELAY, true);
        media.configureBlocking(false);
        media.connect(remoteAddress.getAddress().toInetSocketAddress());
        return true;
      } catch (Exception e) {
        LOG.trace("Failed to begin connect to {}", remoteAddress, e);
        doClose();
        return false;
      }
    } else {
      return false;
    }
  }

  public void finishConnect() {
    try {
      media.finishConnect();
      if (STATE_FIELD.compareAndSet(this, CONNECTING, CONNECTED)) {
        listener.onChannelConnected(this);
      }

      connectAttempt = 0;
    } catch (IOException e) {
      LOG.trace("Failed to finish connect to {}", remoteAddress, e);
      doClose();
    }
  }

  public boolean isClosed() {
    return STATE_FIELD.get(this) == CLOSED;
  }

  public boolean isConnecting() {
    return STATE_FIELD.get(this) == CONNECTING;
  }

  protected void doClose() {
    try {
      if (media != null) {

        try {
          synchronized (registeredKeys) {
            registeredKeys.forEach(k -> k.cancel());
            registeredKeys.clear();
          }
        } finally {
          media.close();
        }
      }

      allocatedBuffer.close();
    } catch (Exception e) {
      LOG.debug("Failed to close channel", e);
    } finally {
      // invoke listener only once and only if connected was invoked as well
      final int previousState = STATE_FIELD.getAndSet(this, CLOSED);

      // ensuring to only invoke this once per channel
      if (previousState != CLOSED) {
        if (listener != null) {
          final boolean wasConnected = previousState == CONNECTED;
          listener.onChannelClosed(this, wasConnected);
        }
      }
    }
  }

  public RemoteAddressImpl getRemoteAddress() {
    return remoteAddress;
  }

  public void interrupt() {
    doClose();
  }

  public void close() {
    doClose();
  }

  public SocketChannel getNioChannel() {
    return media;
  }

  public int getOpenAttempt() {
    return connectAttempt;
  }

  @Override
  public String toString() {
    return media != null ? media.toString() : "unconnected channel to remote " + remoteAddress;
  }

  public interface ChannelLifecycleListener {
    void onChannelConnected(TransportChannel transportChannelImpl);

    void onChannelClosed(TransportChannel transportChannelImpl, boolean wasConnected);
  }
}
