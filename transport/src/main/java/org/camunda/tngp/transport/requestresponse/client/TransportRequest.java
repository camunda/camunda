package org.camunda.tngp.transport.requestresponse.client;

import java.util.concurrent.TimeUnit;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public interface TransportRequest extends AutoCloseable
{
    boolean isOpen();

    MutableDirectBuffer getRequestBuffer();

    MutableDirectBuffer getClaimedRequestBuffer();

    int getClaimedOffset();

    void commit();

    void abort();

    void close();

    long getRequestTimeout();

    DirectBuffer getResponseBuffer();

    int getResponseLength();

    boolean pollResponse();

    void awaitResponse();

    boolean awaitResponse(long timeout, TimeUnit timeUnit);

    boolean isResponseAvailable();
}