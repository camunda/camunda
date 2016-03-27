package org.camunda.tngp.transport;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

public interface TransportChannel extends AutoCloseable
{
    int getId();

    CompletableFuture<TransportChannel> closeAsync();

    void sendControlFrame(ByteBuffer frame);

}