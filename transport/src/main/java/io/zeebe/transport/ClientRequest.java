package io.zeebe.transport;

import java.util.concurrent.Future;

import org.agrona.DirectBuffer;

public interface ClientRequest extends AutoCloseable, Future<DirectBuffer>
{
    long getRequestId();

    /**
     * Same as {@link #get()}, but throws runtime exceptions
     */
    DirectBuffer join();

    @Override
    void close();
}
