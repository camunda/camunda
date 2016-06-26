package org.camunda.tngp.client;

import java.util.concurrent.Future;

import org.camunda.tngp.transport.requestresponse.client.TransportConnection;

public interface ClientCommand<R>
{

    /**
     * Executes the command and blocks until the result is available.
     * Throws {@link RuntimeException} in case the command times out.
     *
     * @return the result of the command.
     */
    R execute();

    /**
     * Executes the command and blocks until the result is available.
     * Throws {@link RuntimeException} in case the command times out.
     *
     * @return the result of the command.
     */
    R execute(TransportConnection connection);

    /**
     * Executes the command asynchronously and returns control to the client thread.
     *
     * @return the {@link AsyncResult}
     */
    Future<R> executeAsync(TransportConnection connection);

}
