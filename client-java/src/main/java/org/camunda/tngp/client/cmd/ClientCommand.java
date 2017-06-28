package org.camunda.tngp.client.cmd;

import java.util.concurrent.Future;

import org.camunda.tngp.client.impl.Topic;

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
     * Executes the command asynchronously and returns control to the client thread.
     *
     * @return a future of the command result
     */
    Future<R> executeAsync();

    /**
     * The topic of the command. Returns {@code null} if the command is not related to a topic.
     *
     * @return the topic related to this command, or null
     */
    Topic getTopic();

}
