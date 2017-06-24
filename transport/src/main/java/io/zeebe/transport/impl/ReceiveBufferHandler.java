package io.zeebe.transport.impl;

import org.agrona.DirectBuffer;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.FragmentHandler;

public class ReceiveBufferHandler implements FragmentHandler
{

    protected final Dispatcher receiveBuffer;

    public ReceiveBufferHandler(Dispatcher receiveBuffer)
    {
        this.receiveBuffer = receiveBuffer;
    }

    @Override
    public int onFragment(DirectBuffer buffer, int offset, int length, int streamId, boolean isMarkedFailed)
    {
        if (receiveBuffer == null)
        {
            return CONSUME_FRAGMENT_RESULT;
        }

        if (!isMarkedFailed)
        {
            final long offerPosition = receiveBuffer.offer(buffer, offset, length, streamId);
            if (offerPosition == -2)
            {
                return POSTPONE_FRAGMENT_RESULT;
            }
            else if (offerPosition == -1)
            {
                return FAILED_FRAGMENT_RESULT;
            }
            else
            {
                return CONSUME_FRAGMENT_RESULT;
            }
        }
        else
        {
            return CONSUME_FRAGMENT_RESULT;
        }
    }

}
