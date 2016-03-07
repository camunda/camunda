package net.long_running.transport;

import uk.co.real_logic.agrona.DirectBuffer;

@FunctionalInterface
public interface ChannelFrameHandler
{

    void onFrameAvailable(DirectBuffer buffer, int offset, int length);

    ChannelFrameHandler DISCARD_HANDLER = (buffer,offset,length) ->
    {
        // discard
    };
}
