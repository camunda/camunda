package io.zeebe.transport;

import org.agrona.DirectBuffer;

public interface ClientInputListener
{

    void onResponse(int streamId, long requestId, DirectBuffer buffer, int offset, int length);
    void onMessage(int streamId, DirectBuffer buffer, int offset, int length);
}
