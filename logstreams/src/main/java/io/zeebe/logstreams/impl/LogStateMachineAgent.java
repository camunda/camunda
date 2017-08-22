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
package io.zeebe.logstreams.impl;

import io.zeebe.util.state.StateMachine;
import io.zeebe.util.state.StateMachineAgent;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Represents the log state machine agent which is used by the log stream controller's.
 * Contains the open, close and also the async methods to which the controller's delegates.
 *
 * The LogStateMachineAgent contains openState and closedState workers which are called if
 * the open or closed states is reached.
 */
public class LogStateMachineAgent extends StateMachineAgent<LogContext>
{
    public static final int TRANSITION_DEFAULT = 0;
    public static final int TRANSITION_OPEN = 1;
    public static final int TRANSITION_CLOSE = 2;

    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final Runnable openStateRunnable;
    private final Runnable closedStateRunnable;

    private CompletableFuture<Void> closeFuture;
    private CompletableFuture<Void> openFuture;

    public LogStateMachineAgent(StateMachine<LogContext> stateMachine, Runnable openStateRunnable, Runnable closedStateRunnable)
    {
        super(stateMachine);
        this.openStateRunnable = openStateRunnable;
        this.closedStateRunnable = closedStateRunnable;
    }

    // open ////////////////////////////////////////////////////////////////////////////////////
    public void open()
    {
        try
        {
            openAsync().get();
        }
        catch (ExecutionException e)
        {
            throw new RuntimeException(e.getCause() != null ? e.getCause() : e);
        }
        catch (InterruptedException e)
        {
            throw new RuntimeException(e);
        }
    }

    public CompletableFuture<Void> openAsync()
    {
        final CompletableFuture<Void> future = new CompletableFuture<>();

        this.addCommand(context ->
        {
            final boolean opening = context.tryTake(TRANSITION_OPEN);
            if (opening)
            {
                openFuture = future;
            }
            else
            {
                future.completeExceptionally(new IllegalStateException("Cannot open log stream. State is not closed."));
            }
        });

        if (isRunning.compareAndSet(false, true))
        {
            try
            {
                openStateRunnable.run();
            }
            catch (Exception e)
            {
                isRunning.set(false);
                openFuture.completeExceptionally(e);
            }
        }

        return future;
    }

    public void completeOpenFuture(Throwable throwable)
    {
        if (throwable == null)
        {
            openFuture.complete(null);
        }
        else
        {
            openFuture.completeExceptionally(throwable);
        }
        openFuture = null;
    }

    // close ////////////////////////////////////////////////////////////////////

    public void close()
    {
        try
        {
            closeAsync().get();
        }
        catch (ExecutionException e)
        {
            throw new RuntimeException(e.getCause() != null ? e.getCause() : e);
        }
        catch (InterruptedException e)
        {
            throw new RuntimeException(e);
        }
    }

    public CompletableFuture<Void> closeAsync()
    {
        final CompletableFuture<Void> future = new CompletableFuture<>();

        this.addCommand(context ->
        {
            final boolean closing = context.tryTake(TRANSITION_CLOSE);
            if (closing)
            {
                closeFuture = future;
            }
            else
            {
                future.completeExceptionally(new IllegalStateException("Cannot close log stream. State is not open."));
            }
        });

        return future;
    }


    public void closing()
    {
        if (isRunning.compareAndSet(true, false))
        {
            closeFuture.complete(null);
            closeFuture = null;
            closedStateRunnable.run();
        }
    }

    public boolean isRunning()
    {
        return isRunning.get();
    }
}
