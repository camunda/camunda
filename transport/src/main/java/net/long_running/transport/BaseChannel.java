package net.long_running.transport;

import uk.co.real_logic.agrona.DirectBuffer;

public interface BaseChannel
{

    long offer(DirectBuffer payload, int offset, int length);

    Integer getId();

    void close();

}