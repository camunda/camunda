package org.camunda.tngp.transport.impl;

import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.*;
import static org.camunda.tngp.transport.impl.TransportControlFrameDescriptor.*;
import static org.camunda.tngp.transport.impl.StaticControlFrames.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import org.camunda.tngp.transport.TransportChannel;
import org.camunda.tngp.transport.impl.agent.ReceiverCmd;
import org.camunda.tngp.transport.impl.agent.SenderCmd;
import org.camunda.tngp.transport.impl.agent.TransportConductorCmd;
import org.camunda.tngp.transport.spi.TransportChannelHandler;

import uk.co.real_logic.agrona.LangUtil;
import uk.co.real_logic.agrona.concurrent.ManyToOneConcurrentArrayQueue;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public abstract class TransportChannelImpl implements TransportChannel
{
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

    protected final ManyToOneConcurrentArrayQueue<SenderCmd> senderCmdQueue;
    protected final ManyToOneConcurrentArrayQueue<ReceiverCmd> receiverCmdQueue;
    protected final ManyToOneConcurrentArrayQueue<TransportConductorCmd> conductorCmdQueue;

    protected CompletableFuture<TransportChannel> closeFuture;

    protected SocketChannel media;
    protected ByteBuffer controlFrame;

    public TransportChannelImpl(
            final TransportContext transportContext,
            final TransportChannelHandler channelHandler)
    {
        this.channelHandler = channelHandler;
        this.maxMessageLength = transportContext.getMaxMessageLength();

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

                    if (msgType == TYPE_MESSAGE)
                    {
                        handled = channelHandler.onChannelReceive(this,
                                channelReadBufferView,
                                HEADER_LENGTH,
                                msgLength);
                    }
                    else
                    {
                        handleControlFrame(msgType, frameLength);
                        handled = true;
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
        }

        return bytesRead;
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

    protected void handleControlFrame(final int msgType, final int frameLength)
    {
        if (msgType == TYPE_CONTROL_END_OF_STREAM)
        {
            mediaClose();
            notifyClosed();
        }
        else if (msgType == TYPE_CONTROL_CLOSE)
        {

            final int newState = STATE_FIELD.updateAndGet(this, (state) -> (state == STATE_CONNECTED
                                                                            || (state == STATE_CLOSE_INITIATED && isServer)) ? STATE_CLOSE_RECEIVED : state);

            if (newState == STATE_CLOSE_RECEIVED)
            {
                senderCmdQueue.add((sender) ->
                {
                    sender.sendControlFrame(this);
                });
            }
        }
        else if (msgType == TYPE_PROTO_CONTROL_FRAME)
        {
            this.channelHandler.onControlFrame(this, channelReadBufferView, 0, frameLength);
        }
        else
        {
            System.err.println("Recevied unhandled control frame of type " + msgType);
        }
    }

    @Override
    public void sendControlFrame(ByteBuffer frame)
    {
        this.controlFrame = frame.duplicate();

        senderCmdQueue.add((s) ->
        {
            s.sendControlFrame(this);
        });

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
            closeForcibly(e);
        }

        return bytesWritten;
    }

    public void writeControlFrame()
    {
        final int state = STATE_FIELD.get(this); // get volatile

        if (state == STATE_CLOSE_INITIATED)
        {
            writeControlFrame(CLOSE_FRAME);
        }
        else if (state == STATE_CLOSE_RECEIVED)
        {
            writeControlFrame(END_OF_STREAM_FRAME);
        }
        else if (controlFrame != null)
        {
            writeControlFrame(controlFrame);
        }
    }

    protected void writeControlFrame(ByteBuffer controlFrame)
    {
        try
        {
            while (controlFrame.hasRemaining())
            {
                media.write(controlFrame);
            }
        }
        catch (IOException e)
        {
            closeForcibly(e);
        }
        finally
        {
            controlFrame.clear();
        }
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

            senderCmdQueue.add((s) ->
            {
                s.sendControlFrame(this);
            });

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
        originalException.printStackTrace();
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
}