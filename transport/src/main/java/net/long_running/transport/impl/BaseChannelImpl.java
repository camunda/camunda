package net.long_running.transport.impl;

import static net.long_running.transport.impl.TransportControlFrameDescriptor.*;
import static net.long_running.transport.ChannelErrorHandler.*;
import static net.long_running.dispatcher.AsyncCompletionCallback.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;


import net.long_running.dispatcher.AsyncCompletionCallback;
import net.long_running.dispatcher.Dispatcher;
import net.long_running.transport.BaseChannel;
import net.long_running.transport.ChannelErrorHandler;
import net.long_running.transport.ChannelFrameHandler;
import net.long_running.transport.impl.agent.ReceiverCmd;
import net.long_running.transport.impl.agent.SenderCmd;
import uk.co.real_logic.agrona.DirectBuffer;
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

    protected Integer id = new Random().nextInt();

    protected final Dispatcher sendBuffer;
    protected final ByteBuffer receiveBuffer;
    protected final UnsafeBuffer receiveBufferView;
    protected final int maxMessageLength;
    protected final ManyToOneConcurrentArrayQueue<SenderCmd> senderCmdQueue;
    protected final ManyToOneConcurrentArrayQueue<ReceiverCmd> receiverCmdQueue;


    protected ChannelFrameHandler frameHandler;
    protected ChannelErrorHandler errorHandler;

    protected SocketChannel media;

    /** set while the channel is in state {@link State#CLOSE_INITIATED} until it is closed */
    protected AsyncCompletionCallback<Boolean> closeCallback;

    public BaseChannelImpl(
            final TransportContext transportContext,
            final ChannelFrameHandler channelReader,
            final ChannelErrorHandler errorHandler)
    {
        this.sendBuffer = transportContext.getSendBuffer();
        this.maxMessageLength = transportContext.getMaxMessageLength();
        senderCmdQueue = transportContext.getSenderCmdQueue();
        receiverCmdQueue = transportContext.getReceiverCmdQueue();

        this.frameHandler = channelReader;
        this.errorHandler = errorHandler;

        if(sendBuffer.getMaxFrameLength() < maxMessageLength)
        {
            throw new RuntimeException("Cannot create Channel: max frame length in writeBuffer "
                    +sendBuffer.getMaxFrameLength()+" is less than transport's '"+maxMessageLength+"'.");
        }

        this.receiveBuffer = ByteBuffer.allocateDirect(maxMessageLength);
        this.receiveBufferView = new UnsafeBuffer(receiveBuffer);
    }

    @Override
    public long offer(final DirectBuffer payload, final int offset, final int length)
    {
        if(state != State.CONNECTED)
        {
            throw new IllegalStateException("Channel is not connected");
        }

        // use channel id as stream id in the shared write buffer
        long position = sendBuffer.offer(payload, offset, length, id);

        if(position > 0)
        {
            // return the position of the message itself, not the next message
            // TODO: turn this into the default behavior of the dispatcher
            // https://github.com/meyerdan/dispatcher/issues/5
            position -= alignedLength(length);
        }

        return position;
    }

    public int receive()
    {

        final int bytesRead = mediaReceive(receiveBuffer);

        if (bytesRead > 0)
        {
            final int available = receiveBuffer.position();

            if(available >= HEADER_LENGTH)
            {
                final int msgLength = receiveBufferView.getInt(lengthOffset(0));
                final int frameLength = alignedLength(msgLength);

                if(available >= frameLength)
                {
                    final int msgType = receiveBufferView.getShort(typeOffset(0));

                    if (msgType == TYPE_MESSAGE)
                    {
                        try
                        {
                            frameHandler.onFrameAvailable(receiveBufferView, HEADER_LENGTH, msgLength);
                        }
                        catch(RuntimeException e)
                        {
                            e.printStackTrace();
                        }
                    }
                    else
                    {
                        handleControlFrame(msgType);
                    }

                    receiveBuffer.limit(available);
                    receiveBuffer.position(frameLength);
                    receiveBuffer.compact();
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
        return sendBuffer;
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
    public Integer getId()
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

    public void setChannelFrameHandler(ChannelFrameHandler frameHandler)
    {
        this.frameHandler = frameHandler;
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

    @Override
    public void close(AsyncCompletionCallback<Boolean> completionCallback)
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
    public boolean closeSync() throws InterruptedException
    {
        // TODO: make sure this is not called from a transport thread !!
        final CompletableFuture<Boolean> future = new CompletableFuture<>();

        close(completeFuture(future));

        try
        {
            return future.get();
        }
        catch (ExecutionException e)
        {
            LangUtil.rethrowUnchecked(e);
        }

        return false;

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

}