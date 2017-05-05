package org.camunda.tngp.transport.impl;

import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.HEADER_LENGTH;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.TYPE_MESSAGE;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.alignedLength;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.lengthOffset;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.typeOffset;
import static org.camunda.tngp.transport.impl.StaticControlFrames.CLOSE_FRAME;
import static org.camunda.tngp.transport.impl.StaticControlFrames.END_OF_STREAM_FRAME;
import static org.camunda.tngp.transport.impl.TransportControlFrameDescriptor.TYPE_CONTROL_CLOSE;
import static org.camunda.tngp.transport.impl.TransportControlFrameDescriptor.TYPE_CONTROL_END_OF_STREAM;
import static org.camunda.tngp.transport.impl.TransportControlFrameDescriptor.TYPE_CONTROL_KEEP_ALIVE;
import static org.camunda.tngp.transport.impl.TransportControlFrameDescriptor.TYPE_PROTO_CONTROL_FRAME;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import org.agrona.DirectBuffer;
import org.agrona.LangUtil;
import org.agrona.concurrent.ManyToOneConcurrentArrayQueue;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.ringbuffer.ManyToOneRingBuffer;
import org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor;
import org.camunda.tngp.transport.TransportChannel;
import org.camunda.tngp.transport.impl.agent.ReceiverCmd;
import org.camunda.tngp.transport.impl.agent.SenderCmd;
import org.camunda.tngp.transport.impl.agent.TransportConductorCmd;
import org.camunda.tngp.transport.spi.TransportChannelHandler;

public abstract class TransportChannelImpl implements TransportChannel
{
    protected static final int INITIAL_CONTROL_FRAME_BUFFER_SIZE = 32;

    public static final int STATE_CONNECTING = 0;
    public static final int STATE_CONNECTED = 1;
    public static final int STATE_CLOSE_INITIATED = 2;
    public static final int STATE_CLOSE_RECEIVED = 3;
    public static final int STATE_CLOSED = 4;

    protected static final AtomicIntegerFieldUpdater<TransportChannelImpl> STATE_FIELD = AtomicIntegerFieldUpdater.newUpdater(TransportChannelImpl.class, "state");

    protected volatile int state;

    protected int id;

    protected final boolean isServer = ServerChannelImpl.class.isAssignableFrom(getClass()); // wtf?

    protected final ByteBuffer channelReadBuffer;
    protected final UnsafeBuffer channelReadBufferView;
    protected final int maxMessageLength;
    protected final TransportChannelHandler channelHandler;

    protected final ManyToOneRingBuffer controlFramesBuffer;
    protected final ManyToOneConcurrentArrayQueue<SenderCmd> senderCmdQueue;
    protected final ManyToOneConcurrentArrayQueue<ReceiverCmd> receiverCmdQueue;
    protected final ManyToOneConcurrentArrayQueue<TransportConductorCmd> conductorCmdQueue;

    protected CompletableFuture<TransportChannel> closeFuture;

    protected SocketChannel media;
    protected ByteBuffer controlFrameBuffer = ByteBuffer.allocate(INITIAL_CONTROL_FRAME_BUFFER_SIZE);

    public TransportChannelImpl(
            final TransportContext transportContext,
            final TransportChannelHandler channelHandler)
    {
        this.channelHandler = channelHandler;
        this.maxMessageLength = transportContext.getMaxMessageLength();

        controlFramesBuffer = transportContext.getControlFrameBuffer();
        senderCmdQueue = transportContext.getSenderCmdQueue();
        receiverCmdQueue = transportContext.getReceiverCmdQueue();
        conductorCmdQueue = transportContext.getConductorCmdQueue();

        this.channelReadBuffer = ByteBuffer.allocateDirect(maxMessageLength * 2);
        this.channelReadBufferView = new UnsafeBuffer(channelReadBuffer);

        STATE_FIELD.set(this, STATE_CONNECTING);
    }

    public int receive()
    {
        final int bytesRead = mediaReceive(channelReadBuffer);
        int available = channelReadBuffer.position();

        if (bytesRead != -1)
        {
            while (available >= HEADER_LENGTH)
            {
                final int msgLength = channelReadBufferView.getInt(lengthOffset(0));
                final int frameLength = alignedLength(msgLength);

                if (available < frameLength)
                {
                    break;
                }
                else
                {
                    final int msgType = channelReadBufferView.getShort(typeOffset(0));

                    boolean handled = false;

                    if (isProtocolMessage(msgType))
                    {
                        handled = handleProtocolMessage(msgType, msgLength, frameLength);
                    }
                    else
                    {
                        handled = handleGeneralControlFrame(msgType, frameLength);
                    }

                    if (handled)
                    {
                        channelReadBuffer.limit(available);
                        channelReadBuffer.position(frameLength);
                        channelReadBuffer.compact();
                        available -= frameLength;
                    }
                    else
                    {
                        break;
                    }
                }
            }
        }
        else if (bytesRead == -1)
        {
            // stream closed on the other side
            final int state = STATE_FIELD.get(this);

            mediaClose();

            if (state == STATE_CONNECTED)
            {
                notifyCloseException(null);
            }
            else
            {
                notifyClosed();
            }
        }

        return bytesRead;
    }

    protected static boolean isProtocolMessage(final int msgType)
    {
        return msgType == TYPE_MESSAGE || msgType == TYPE_PROTO_CONTROL_FRAME;
    }

    private int mediaReceive(ByteBuffer receiveBuffer)
    {
        int bytesReceived = -2;

        try
        {
            bytesReceived = media.read(receiveBuffer);
        }
        catch (IOException e)
        {
            closeForcibly(e);
        }

        return bytesReceived;
    }

    /**
     * @param msgType see {@link DataFrameDescriptor}
     * @param messageLength exact message length
     * @param frameLength aligned message length
     */
    protected boolean handleProtocolMessage(int msgType, int messageLength, int frameLength)
    {
        if (msgType == TYPE_MESSAGE)
        {
            return channelHandler.onChannelReceive(this,
                    channelReadBufferView,
                    HEADER_LENGTH,
                    messageLength);
        }
        else
        {
            return channelHandler.onControlFrame(this, channelReadBufferView, 0, frameLength);
        }
    }

    protected boolean handleGeneralControlFrame(final int msgType, final int frameLength)
    {
        if (msgType == TYPE_CONTROL_END_OF_STREAM)
        {
            mediaClose();
            notifyClosed();
            return true;
        }
        else if (msgType == TYPE_CONTROL_CLOSE)
        {

            final int newState = STATE_FIELD.updateAndGet(this, (state) -> (state == STATE_CONNECTED
                                                                            || (state == STATE_CLOSE_INITIATED && isServer)) ? STATE_CLOSE_RECEIVED : state);

            if (newState == STATE_CLOSE_RECEIVED)
            {
                return scheduleControlFrame(END_OF_STREAM_FRAME, 0, END_OF_STREAM_FRAME.capacity());
            }
            else
            {
                return true;
            }
        }
        else if (msgType == TYPE_CONTROL_KEEP_ALIVE)
        {
            channelHandler.onChannelKeepAlive(this);
            return true;
        }
        else
        {
            System.err.println("Recevied unhandled control frame of type " + msgType);
            return true;
        }
    }


    /**
     * @return true if control frame could be completely written to socket channel
     */
    public boolean trySendControlFrame()
    {
        try
        {
            media.write(controlFrameBuffer);
            return !controlFrameBuffer.hasRemaining();
        }
        catch (IOException e)
        {
            e.printStackTrace();
            closeForcibly(e);
            return false;
        }
    }

    public void initControlFrame(DirectBuffer buf, int offset, int length)
    {
        controlFrameBuffer.clear();
        if (length > controlFrameBuffer.capacity())
        {
            controlFrameBuffer = ByteBuffer.allocate(length);
        }

        buf.getBytes(offset, controlFrameBuffer, length);
        controlFrameBuffer.flip();

    }

    @Override
    public boolean scheduleControlFrame(DirectBuffer frame, int offset, int length)
    {
        return controlFramesBuffer.write(id, frame, offset, length);
    }

    @Override
    public boolean scheduleControlFrame(DirectBuffer frame)
    {
        return scheduleControlFrame(frame, 0, frame.capacity());
    }

    public int write(ByteBuffer buffer)
    {
        int bytesWritten = -1;

        try
        {
            bytesWritten = media.write(buffer);
        }
        catch (IOException e)
        {
            e.printStackTrace();
            closeForcibly(e);
        }

        return bytesWritten;
    }

    @Override
    public int getId()
    {
        return id;
    }

    public void registerSelector(Selector selector, int ops)
    {
        try
        {
            final SelectionKey key = media.register(selector, ops);
            key.attach(this);
        }
        catch (ClosedChannelException e)
        {
            LangUtil.rethrowUnchecked(e);
        }
    }

    public void removeSelector(Selector selector)
    {
        final SelectionKey key = media.keyFor(selector);
        if (key != null)
        {
            key.cancel();
        }
    }

    public void mediaClose()
    {
        STATE_FIELD.set(this, STATE_CLOSED);

        try
        {
            media.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        finally
        {
            senderCmdQueue.add((sender) ->
            {
                sender.removeChannel(this);
            });
            receiverCmdQueue.add((receiver) ->
            {
                receiver.removeChannel(this);
            });
        }
    }

    @Override
    public CompletableFuture<TransportChannel> closeAsync()
    {
        if (STATE_FIELD.compareAndSet(this, STATE_CONNECTED, STATE_CLOSE_INITIATED))
        {
            this.closeFuture = new CompletableFuture<>();

            final boolean controlFrameScheduled = scheduleControlFrame(CLOSE_FRAME, 0, CLOSE_FRAME.capacity());
            if (!controlFrameScheduled)
            {
                // TODO: react to failure properly (=> should retry later)
                throw new RuntimeException("Could not singal that channel should be closed");
            }

            return this.closeFuture;
        }
        else
        {
            return CompletableFuture.completedFuture(this);
        }
    }

    @Override
    public void close()
    {
        closeAsync().join();
    }

    public void closeForcibly(IOException originalException)
    {
        mediaClose();
        notifyCloseException(originalException);
    }

    protected void notifyCloseException(Exception e)
    {
        conductorCmdQueue.add((c) ->
        {
            c.onChannelClosedExceptionally(this, closeFuture, e);
        });
    }

    protected void notifyClosed()
    {
        conductorCmdQueue.add((c) ->
        {
            c.onChannelClosed(this, closeFuture);
        });
    }

    public void setId(int id)
    {
        this.id = id;
    }

    public TransportChannelHandler getChannelHandler()
    {
        return channelHandler;
    }

    @Override
    public boolean isOpen()
    {
        return isInState(STATE_CONNECTED);
    }

    @Override
    public boolean isClosed()
    {
        return isInState(STATE_CLOSED);
    }

    @Override
    public boolean isConnecting()
    {
        return isInState(STATE_CONNECTING);
    }

    protected boolean isInState(int state)
    {
        return STATE_FIELD.get(this) == state;
    }


}