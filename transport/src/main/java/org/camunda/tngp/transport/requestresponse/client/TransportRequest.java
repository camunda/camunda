package org.camunda.tngp.transport.requestresponse.client;

import java.util.concurrent.TimeUnit;

import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.MutableDirectBuffer;

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