package net.long_running.transport.impl;

import static uk.co.real_logic.agrona.BitUtil.*;
import static net.long_running.dispatcher.impl.log.DataFrameDescriptor.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Random;

import net.long_running.dispatcher.Dispatcher;
import net.long_running.transport.BaseChannel;
import net.long_running.transport.ChannelFrameHandler;
import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.LangUtil;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public abstract class BaseChannelImpl implements BaseChannel
{

    public static enum State
    {
        NEW, CONNECTED, CLOSING, CLOSED;
    }

    protected State state = State.NEW;
    protected Integer id = new Random().nextInt();

    protected final Dispatcher sendBuffer;
    protected final ByteBuffer receiveBuffer;
    protected final UnsafeBuffer receiveBufferView;
    protected final int maxMessageLength;

    protected ChannelFrameHandler frameHandler;
    protected SocketChannel media;

    public BaseChannelImpl(ChannelFrameHandler channelReader, TransportContext transportContext)
    {
        this.frameHandler = channelReader;
        this.sendBuffer = transportContext.getSendBuffer();
        this.maxMessageLength = transportContext.getMaxMessageLength();

        if(sendBuffer.getMaxFrameLength() < maxMessageLength)
        {
            throw new RuntimeException("Cannot create Channel: max frame length in writeBuffer "
                    +sendBuffer.getMaxFrameLength()+" is less than transport's '"+maxMessageLength+"'.");
        }

        this.receiveBuffer = ByteBuffer.allocateDirect(maxMessageLength);
        this.receiveBufferView = new UnsafeBuffer(receiveBuffer);
    }

    /* (non-Javadoc)
     * @see net.long_running.transport.impl.BaseChannel#offer(uk.co.real_logic.agrona.DirectBuffer, int, int)
     */
    @Override
    public long offer(final DirectBuffer payload, final int offset, final int length)
    {
        if(maxMessageLength < length)
        {
            throw new IllegalArgumentException("Message length is larger than max message length of "+maxMessageLength);
        }
        if(state != State.CONNECTED)
        {
            throw new IllegalStateException("Channel is not connected");
        }

        // use channel id as stream id in the shared write buffer
        return sendBuffer.offer(payload, offset, length, id);
    }

    public int receive()
    {
        int bytesRead = 0;

        try
        {
            bytesRead = media.read(receiveBuffer);
            if(receiveBuffer.position() > SIZE_OF_INT)
            {
                int msgLength = receiveBufferView.getInt(0);
                int frameLength = aligedLength(msgLength);
                if(receiveBuffer.position() >= frameLength)
                {
                    try
                    {
                        frameHandler.onFrameAvailable(receiveBufferView, HEADER_LENGTH, msgLength);
                    }
                    catch(RuntimeException e)
                    {
                        // TODO
                        e.printStackTrace();
                    }
                    finally
                    {
                        receiveBuffer.limit(receiveBuffer.position());
                        receiveBuffer.position(frameLength);
                        receiveBuffer.compact();
                    }
                }
            }
        }
        catch (IOException e)
        {
            // TODO
        }

        if(bytesRead == -1)
        {
            state = State.CLOSING;
        }

        return bytesRead;
    }

    public Dispatcher getWriteBuffer()
    {
        return sendBuffer;
    }

    public void write(ByteBuffer buffer)
    {

        try
        {
            while(buffer.hasRemaining())
            {
                media.write(buffer);
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

    }

    @Override
    public Integer getId()
    {
        return id;
    }

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

    @Override
    public abstract void close();

    public void setChannelFrameHandler(ChannelFrameHandler frameHandler)
    {
        this.frameHandler = frameHandler;
    }

    public void closeConnection()
    {
        if(this.state == State.CLOSING)
        {
            try
            {
                media.close();
            }
            catch (IOException e)
            {
                // TODO
                e.printStackTrace();
            }
            finally
            {
                this.state = State.CLOSED;
            }
        }
    }
}