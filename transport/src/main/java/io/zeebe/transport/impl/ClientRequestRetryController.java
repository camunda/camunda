package io.zeebe.transport.impl;

import java.time.Duration;
import java.util.Deque;
import java.util.LinkedList;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.function.Supplier;

import io.zeebe.transport.*;
import io.zeebe.util.buffer.BufferWriter;
import io.zeebe.util.buffer.DirectBufferWriter;
import io.zeebe.util.sched.ActorClock;
import io.zeebe.util.sched.ZbActor;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import io.zeebe.util.time.ClockUtil;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class ClientRequestRetryController extends ZbActor
{
    private static final Duration RESUBMIT_TIMEOUT = Duration.ofMillis(1);

    private final CompletableActorFuture<ClientRequest> successfulRequest = new CompletableActorFuture<>();

    private final ClientRequestPool requestPool;

    private final Supplier<ActorFuture<RemoteAddress>> remoteAddressSupplier;
    private final Duration timeout;
    private final long deadline;

    private final Function<DirectBuffer, Boolean> responseHandler;

    private final UnsafeBuffer writeBuffer = new UnsafeBuffer(0, 0);
    private final DirectBufferWriter requestWriter = new DirectBufferWriter();

    private final Deque<RemoteAddress> remotesTried = new LinkedList<>();

    public ClientRequestRetryController(
            Supplier<ActorFuture<RemoteAddress>> remoteAddressSupplier,
            Function<DirectBuffer, Boolean> responseInspector,
            ClientRequestPool requestPool,
            BufferWriter writer,
            Duration timeout)
    {
        this.remoteAddressSupplier = remoteAddressSupplier;
        this.responseHandler = responseInspector;
        this.requestPool = requestPool;
        this.timeout = timeout;
        this.deadline = ClockUtil.getCurrentTimeInMillis() + timeout.toMillis();

        final int requestLength = writer.getLength();
        writeBuffer.wrap(new byte[requestLength]); // TODO: we need to pool / recycle this memory
        writer.write(writeBuffer, 0);
        requestWriter.wrap(writeBuffer, 0, requestLength);
    }

    @Override
    protected void onActorStarted()
    {
        actor.run(this::getRemoteAddress);
    }

    private void getRemoteAddress()
    {
        actor.await(remoteAddressSupplier.get(), (address, throwable) ->
        {
            if (remotesTried.isEmpty() || !address.equals(remotesTried.peek()))
            {
                remotesTried.push(address);
            }

            actor.runUntilDone(this::sendRequest);
        });
    }

    private void sendRequest()
    {
        if (ActorClock.currentTimeMillis() > deadline)
        {
            actor.done();
            actor.run(this::onRequestTimedOut);
        }
        else
        {
            final ClientRequestImpl request = requestPool.openRequest(remotesTried.peek(), requestWriter);

            if (request != null)
            {
                actor.done();

                // TODO: add max await until deadline
                actor.await(request, (DirectBuffer response, Throwable e) ->
                {
                    boolean shouldRetry = false;

                    if (e != null)
                    {
                        shouldRetry = e instanceof ExecutionException && e.getCause() instanceof NotConnectedException;
                        System.out.println("Retrying send");
                    }
                    else
                    {
                        shouldRetry = responseHandler.apply(response);
                    }

                    if (!shouldRetry)
                    {
                        successfulRequest.complete(request);
                    }
                    else
                    {
                        request.close();
                        actor.runDelayed(RESUBMIT_TIMEOUT, this::getRemoteAddress);
                    }
                });
            }
            else
            {
                actor.yield(); // retry send
            }
        }
    }

    private void onRequestTimedOut()
    {
        final StringBuilder errBuilder = new StringBuilder("Request timed out after ")
                 .append(timeout)
                 .append(".\nRemotes tried (in reverse order):\n");

        for (RemoteAddress remoteAddress : remotesTried)
        {
            errBuilder.append("\t")
                .append(remoteAddress)
                .append("\n");
        }

        final String errorMessage = errBuilder.toString();
        successfulRequest.completeExceptionally(errorMessage, new RequestTimeoutException(errorMessage));
    }

    public ActorFuture<ClientRequest> getRequest()
    {
        return successfulRequest;
    }
}
