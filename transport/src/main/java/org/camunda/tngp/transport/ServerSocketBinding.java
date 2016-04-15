package org.camunda.tngp.transport;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;

public interface ServerSocketBinding extends AutoCloseable
{
    InetSocketAddress getBindAddress();

    CompletableFuture<ServerSocketBinding> closeAsync();

    @Override
    void close();
}