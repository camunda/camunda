package org.camunda.tngp.transport.impl;

import static org.camunda.tngp.transport.ChannelErrorHandler.*;
import static org.camunda.tngp.transport.impl.TransportControlFrameDescriptor.*;
import static org.camunda.tngp.dispatcher.AsyncCompletionCallback.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import org.camunda.tngp.dispatcher.AsyncCompletionCallback;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.transport.BaseChannel;
import org.camunda.tngp.transport.ChannelErrorHandler;
import org.camunda.tngp.transport.impl.agent.ReceiverCmd;
import org.camunda.tngp.transport.impl.agent.SenderCmd;

import uk.co.real_logic.agrona.LangUtil;
import uk.co.real_logic.agrona.concurrent.ManyToOneConcurrentArrayQueue;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public abstract class BaseChannelImpl implements BaseChannel
{

    protected static ByteBuffer CLOSE_FRAME = ByteBuffer.allocate(alignedLength(0));
    protected static ByteBuffer END_OF_STREAM_FRAME = ByteBuffer.allocate(alignedLength(0));


    static
    {
        UnsafeBuffer ctrMsgWriter = new UnsafeBuffer(0,0);

        ctrMsgWriter.wrap(CLOSE_FRAME);
        ctrMsgWriter.putInt(lengthOffset(0), 0);
        ctrMsgWriter.putShort(typeOffset(0), TYPE_CONTROL_CLOSE);

        ctrMsgWriter.wrap(END_OF_STREAM_FRAME);
        ctrMsgWriter.putInt(lengthOffset(0), 0);
        ctrMsgWriter.putShort(typeOffset(0), TYPE_CONTROL_END_OF_STREAM);

    }

    public static enum State
    {
        NEW,
        CONNECTED,
        CLOSE_INITIATED,
        CLOSE_RECEIVED,
        CLOSED;
    }

    protected static final AtomicReferenceFieldUpdater<BaseChannelImpl, State> STATE_UPDATER
        = AtomicReferenceFieldUpdater.newUpdater(BaseChannelImpl.class, State.class, "state");

    protected volatile State state = State.NEW;

    protected int id;

    protected final Dispatcher transportSendBuffer;
    protected final Dispatcher transportReceiveBuffer;

    protected final ByteBuffer channelReadBuffer;
    protected final UnsafeBuffer channelReadBufferView;
    protected final int maxMessageLength;
    protected final ManyToOneConcurrentArrayQueue<SenderCmd> senderCmdQueue;
    protected final ManyToOneConcurrentArrayQueue<ReceiverCmd> receiverCmdQueue;


    protected ChannelErrorHandler errorHandler;

    protected SocketChannel media;

    /** set while the channel is in state {@link State#CLOSE_INITIATED} until it is closed */
    protected AsyncCompletionCallback<Boolean> closeCallback;

    public BaseChannelImpl(
            final TransportContext transportContext,
            final ChannelErrorHandler errorHandler)
    {
        this.transportSendBuffer = transportContext.getSendBuffer();
        this.transportReceiveBuffer = transportContext.getReceiveBuffer();
        this.maxMessageLength = transportContext.getMaxMessageLength();

        senderCmdQueue = transportContext.getSenderCmdQueue();
        receiverCmdQueue = transportContext.getReceiverCmdQueue();

        this.errorHandler = errorHandler;

        if(transportSendBuffer.getMaxFrameLength() < maxMessageLength)
        {
            throw new RuntimeException("Cannot create Channel: max frame length in writeBuffer "
                    +transportSendBuffer.getMaxFrameLength()+" is less than transport's '"+maxMessageLength+"'.");
        }

        this.channelReadBuffer = ByteBuffer.allocateDirect(maxMessageLength * 2);
        this.channelReadBufferView = new UnsafeBuffer(channelReadBuffer);
    }

    public int receive()
    {

        final int bytesRead = mediaReceive(channelReadBuffer);

        if (bytesRead > 0)
        {
            int available = channelReadBuffer.position();

            while(available >= HEADER_LENGTH)
            {
                final int msgLength = channelReadBufferView.getInt(lengthOffset(0));
                final int frameLength = alignedLength(msgLength);

                if(available < frameLength)
                {
                    break;
                }
                else {
                    final int msgType = channelReadBufferView.getShort(typeOffset(0));

                    if (msgType == TYPE_MESSAGE)
                    {
                        int attempts = 0;
                        long publishResult = -1;
                        do
                        {
                            publishResult = transportReceiveBuffer.offer(
                                    channelReadBufferView,
                                    HEADER_LENGTH,
                                    msgLength,
                                    id);

                            attempts++;
                        }
                        while(publishResult == -2 || (publishResult == -1 && attempts < 25));

                        if(publishResult == -1)
                        {
                            // TODO: connection is backpressured
                            System.out.println("connection backpressured");
                        }
                    }
                    else
                    {
                        handleControlFrame(msgType);
                    }

                    channelReadBuffer.limit(available);
                    channelReadBuffer.position(frameLength);
                    channelReadBuffer.compact();
                    available -= frameLength;
                }
            }
        }
        else if(bytesRead == -1)
        {
            // stream closed on the other side
            mediaClose();
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

    protected void handleControlFrame(final int msgType)
    {
        if (msgType == TYPE_CONTROL_END_OF_STREAM)
        {
            mediaClose();
            notifyClosed();
        }
        else if (msgType == TYPE_CONTROL_CLOSE)
        {
            final boolean isServer = ServerChannelImpl.class.isAssignableFrom(getClass());

            final State newState = STATE_UPDATER.updateAndGet(this, (state) -> (state == State.CONNECTED
                                                                            || (state == State.CLOSE_INITIATED && isServer)) ? State.CLOSE_RECEIVED : state);

            if(newState == State.CLOSE_RECEIVED)
            {
                senderCmdQueue.add((sender) -> {
                    sender.sendControlFrame(this);
                });
            }
        }
        else
        {
            System.err.println("Recevied unhandled control frame of type "+msgType);
        }
    }

    public Dispatcher getWriteBuffer()
    {
        return transportSendBuffer;
    }

    public void writeMessage(ByteBuffer buffer, long messageId)
    {
        final State state = this.state; // get volatile

        if(state == State.CONNECTED)
        {
            try
            {
                while (buffer.hasRemaining())
                {
                    media.write(buffer);
                }
            }
            catch (IOException e)
            {
                notifyError(SEND_ERROR, messageId);
                closeForcibly(e);
            }
        }
        else
        {
            notifyError(CHANNEL_CLOSED, messageId);
        }
    }

    public void writeControlFrame()
    {
        final State state = this.state; // get volatile

        if (state == State.CLOSE_INITIATED)
        {
            writeControlFrame(CLOSE_FRAME);
        }
        else if (state == State.CLOSE_RECEIVED)
        {
            writeControlFrame(END_OF_STREAM_FRAME);
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
        catch(IOException e)
        {
            closeForcibly(e);
        }
        finally
        {
            controlFrame.clear();
        }
    }

    protected void notifyError(int errorType, long messageId)
    {
        try
        {
            errorHandler.onChannelError(errorType, messageId);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    @Override
    public int getId()
    {
        return id;
    }

    @Override
    public State getState()
    {
        return state;
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
        if(key != null)
        {
            key.cancel();
        }
    }

    public void mediaClose()
    {
        this.state = State.CLOSED;

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

    protected void close(AsyncCompletionCallback<Boolean> completionCallback)
    {
        if(STATE_UPDATER.compareAndSet(this, State.CONNECTED, State.CLOSE_INITIATED))
        {
            this.closeCallback = completionCallback;

            senderCmdQueue.add((s) ->
            {
                s.sendControlFrame(this);
            });

        }
        else
        {
            notifyClosed();
        }
    }

    @Override
    public Future<Boolean> close()
    {
        final CompletableFuture<Boolean> future = new CompletableFuture<>();

        close(completeFuture(future));

        return future;
    }

    public void closeForcibly(IOException originalException)
    {
        mediaClose();
        notifyCloseException(originalException);
    }

    protected void notifyCloseException(Exception e)
    {
        if(closeCallback != null)
        {
            try
            {
                closeCallback.onComplete(e, null);
            }
            catch(Exception ex)
            {
                e.printStackTrace();
            }
        }
    }

    protected void notifyClosed()
    {
        if(closeCallback != null)
        {
            try
            {
                closeCallback.onComplete(null, state == State.CLOSED);
            }
            catch(Exception e)
            {
                e.printStackTrace();
            }
        }
    }

    public void setId(int id)
    {
        this.id = id;
    }

}