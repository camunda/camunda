/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.client.impl;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.agrona.LangUtil;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.zeebe.client.clustering.impl.ClientTopologyManager;
import io.zeebe.client.event.Event;
import io.zeebe.client.event.EventMetadata;
import io.zeebe.client.event.impl.EventImpl;
import io.zeebe.client.impl.cmd.CommandImpl;
import io.zeebe.client.task.impl.ControlMessageRequest;
import io.zeebe.protocol.Protocol;
import io.zeebe.transport.ClientTransport;
import io.zeebe.util.actor.Actor;

public class RequestManager implements Actor
{
    private int capacity;

    protected final RequestController[] commandControllers;
    protected final ArrayBlockingQueue<RequestController> pooledCmds;

    protected final ClientTransport transport;
    protected final ClientTopologyManager topologyManager;

    protected final RequestDispatchStrategy dispatchStrategy;

    public RequestManager(
            final ClientTransport transport,
            final ClientTopologyManager topologyManager,
            RequestDispatchStrategy dispatchStrategy,
            ObjectMapper objectMapper,
            int capacity)
    {
        this.transport = transport;
        this.topologyManager = topologyManager;
        this.capacity = capacity;

        this.pooledCmds = new ArrayBlockingQueue<>(capacity);
        this.commandControllers = new RequestController[capacity];
        this.dispatchStrategy = dispatchStrategy;

        for (int i = 0; i < capacity; i++)
        {
            final RequestController controller = new RequestController(transport, topologyManager, objectMapper, ctrl -> pooledCmds.add(ctrl));
            this.commandControllers[i] = controller;
            this.pooledCmds.add(controller);
        }
    }

    @Override
    public int doWork() throws Exception
    {
        int wc = 0;

        for (int i = 0; i < capacity; i++)
        {
            final RequestController controller = commandControllers[i];
            wc += controller.doWork();
        }

        return wc;
    }

    public <E extends Event> CompletableFuture<E> executeAsync(final CommandImpl<E> command)
    {
        ensureValidTarget(command);

        final CompletableFuture<E> future = new CompletableFuture<>();

        try
        {
            final RequestController ctrl = pooledCmds.take();
            ctrl.configureCommandRequest(command, future);
        }
        catch (InterruptedException e)
        {
            LangUtil.rethrowUnchecked(e);
        }

        return future;
    }

    private <E extends Event> void ensureValidTarget(final CommandImpl<E> command)
    {
        final EventImpl event = command.getEvent();
        if (!event.hasValidPartitionId())
        {
            final EventMetadata metadata = event.getMetadata();
            final int targetPartition = dispatchStrategy.determinePartition(metadata.getTopicName(), metadata.getType(), event.getState());
            event.setPartitionId(targetPartition);
        }
    }

    public String getSystemTopic()
    {
        return Protocol.SYSTEM_TOPIC;
    }

    public int getSystemPartition()
    {
        return Protocol.SYSTEM_PARTITION;
    }

    public <E extends Event> E execute(final CommandImpl<E> command)
    {
        return waitAndResolve(executeAsync(command));
    }


    public <E> CompletableFuture<E> executeAsync(final ControlMessageRequest<E> controlMessage)
    {
        final CompletableFuture<E> future = new CompletableFuture<>();

        try
        {
            final RequestController ctrl = pooledCmds.take();
            ctrl.configureControlMessageRequest(controlMessage, future);
        }
        catch (InterruptedException e)
        {
            LangUtil.rethrowUnchecked(e);
        }

        return future;
    }

    protected <E> E waitAndResolve(CompletableFuture<E> future)
    {
        try
        {
            return future.get();
        }
        catch (final InterruptedException e)
        {
            throw new RuntimeException("Interrupted while executing command");
        }
        catch (final ExecutionException e)
        {
            throw (RuntimeException) e.getCause();
        }
    }

    public <E> E execute(ControlMessageRequest<E> controlMessage)
    {
        return waitAndResolve(executeAsync(controlMessage));
    }
}
