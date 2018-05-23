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

import java.time.Duration;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.Supplier;

import io.zeebe.client.api.ZeebeFuture;
import io.zeebe.client.api.record.Record;
import io.zeebe.client.cmd.*;
import io.zeebe.client.impl.clustering.*;
import io.zeebe.client.impl.data.ZeebeObjectMapperImpl;
import io.zeebe.protocol.clientapi.ErrorCode;
import io.zeebe.protocol.clientapi.MessageHeaderDecoder;
import io.zeebe.transport.*;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.ActorTask;
import io.zeebe.util.sched.clock.ActorClock;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import org.agrona.DirectBuffer;

public class RequestManager extends Actor
{
    protected final ClientOutput output;
    protected final ClientTopologyManager topologyManager;
    protected final ZeebeObjectMapperImpl objectMapper;
    protected final Duration requestTimeout;
    protected final RequestDispatchStrategy dispatchStrategy;
    protected final long blockTimeMillis;

    public RequestManager(
            ClientOutput output,
            ClientTopologyManager topologyManager,
            ZeebeObjectMapperImpl objectMapper,
            Duration requestTimeout,
            long blockTimeMillis)
    {
        this.output = output;
        this.topologyManager = topologyManager;
        this.objectMapper = objectMapper;
        this.requestTimeout = requestTimeout;
        this.blockTimeMillis = blockTimeMillis;
        this.dispatchStrategy = new RoundRobinDispatchStrategy(topologyManager);
    }

    public ActorFuture<Void> close()
    {
        return actor.close();
    }

    public <R extends Record> R execute(final CommandImpl<R> command)
    {
        return waitAndResolve(send(command));
    }

    public <E> E execute(ControlMessageRequest<E> controlMessage)
    {
        return waitAndResolve(send(controlMessage));
    }

    private <R> ResponseFuture<R> executeAsync(final RequestResponseHandler requestHandler)
    {
        final Supplier<RemoteAddress> remoteProvider = determineRemoteProvider(requestHandler);

        final ActorFuture<ClientResponse> responseFuture =
                output.sendRequestWithRetry(remoteProvider, RequestManager::shouldRetryRequest, requestHandler, requestTimeout);

        if (responseFuture != null)
        {
            return new ResponseFuture<>(responseFuture, requestHandler, requestTimeout);
        }
        else
        {
            throw new ClientOutOfMemoryException("Zeebe client is out of buffer memory and cannot make " +
                "new requests until memory is reclaimed.");
        }
    }

    private static boolean shouldRetryRequest(DirectBuffer responseContent)
    {
        final ErrorResponseHandler errorHandler = new ErrorResponseHandler();
        final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
        headerDecoder.wrap(responseContent, 0);

        if (errorHandler.handlesResponse(headerDecoder))
        {
            errorHandler.wrap(responseContent, headerDecoder.encodedLength(), headerDecoder.blockLength(), headerDecoder.version());

            final ErrorCode errorCode = errorHandler.getErrorCode();
            return errorCode == ErrorCode.PARTITION_NOT_FOUND || errorCode == ErrorCode.REQUEST_TIMEOUT;
        }
        else
        {
            return false;
        }
    }

    private ActorFuture<Integer> updateTopologyAndDeterminePartition(String topic)
    {
        final CompletableActorFuture<Integer> future = new CompletableActorFuture<>();

        actor.call(() ->
        {
            final long timeout = ActorClock.currentTimeMillis() + requestTimeout.toMillis();
            updateTopologyAndDeterminePartition(topic, future, timeout);
        });
        return future;
    }

    private void updateTopologyAndDeterminePartition(String topic, CompletableActorFuture<Integer> future, long timeout)
    {
        final ActorFuture<ClusterState> topologyFuture = topologyManager.requestTopology();
        actor.runOnCompletion(topologyFuture, (topology, throwable) ->
        {
            final int partition = dispatchStrategy.determinePartition(topic);
            if (partition >= 0)
            {
                future.complete(partition);
            }
            else if (ActorClock.currentTimeMillis() > timeout)
            {
                future.completeExceptionally(new ClientException("Could not determine target partition in time " + requestTimeout));
            }
            else
            {
                updateTopologyAndDeterminePartition(topic, future, timeout);
            }
        });
    }

    private Supplier<RemoteAddress> determineRemoteProvider(RequestResponseHandler requestHandler)
    {
        if (!requestHandler.addressesSpecificTopic() && !requestHandler.addressesSpecificPartition())
        {
            return new BrokerProvider((topology) -> topology.getRandomBroker());
        }
        else
        {
            final int targetPartition;
            if (!requestHandler.addressesSpecificPartition())
            {
                int proposedPartition = dispatchStrategy.determinePartition(requestHandler.getTargetTopic());

                if (proposedPartition >= 0)
                {
                    targetPartition = proposedPartition;
                }
                else
                {
                    final ActorFuture<Integer> partitionFuture =
                            updateTopologyAndDeterminePartition(requestHandler.getTargetTopic());

                    try
                    {
                        proposedPartition = partitionFuture.get(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
                    }
                    catch (InterruptedException | ExecutionException | TimeoutException e)
                    {
                        proposedPartition = -1;
                    }

                    targetPartition = proposedPartition;
                }
                if (targetPartition < 0)
                {
                    throw new ClientException("Cannot determine target partition for request. Request was: " + requestHandler.describeRequest());
                }
                else
                {
                    requestHandler.onSelectedPartition(targetPartition);
                }
            }
            else
            {
                targetPartition = requestHandler.getTargetPartition();
            }

            return new BrokerProvider((topology) -> topology.getLeaderForPartition(targetPartition));
        }
    }

    public <E> ResponseFuture<E> send(final ControlMessageRequest<E> controlMessage)
    {
        final ControlMessageRequestHandler requestHandler = new ControlMessageRequestHandler(objectMapper, controlMessage);
        return executeAsync(requestHandler);
    }

    public <R extends Record> ResponseFuture<R> send(final CommandImpl<R> command)
    {
        final CommandRequestHandler requestHandler = new CommandRequestHandler(objectMapper, command);
        return executeAsync(requestHandler);
    }

    private <E> E waitAndResolve(Future<E> future)
    {
        try
        {
            return future.get();
        }
        catch (final InterruptedException e)
        {
            throw new RuntimeException("Interrupted while waiting for command result", e);
        }
        catch (final ExecutionException e)
        {
            final Throwable cause = e.getCause();
            if (cause instanceof ClientException)
            {
                throw ((ClientException) cause).newInCurrentContext();
            }
            else
            {
                throw new ClientException("Could not make request", e);
            }
        }
    }

    private class BrokerProvider implements Supplier<RemoteAddress>
    {
        private int attempt = 0;

        private final Function<ClusterStateImpl, RemoteAddress> addressStrategy;

        BrokerProvider(Function<ClusterStateImpl, RemoteAddress> addressStrategy)
        {
            this.addressStrategy = addressStrategy;
        }

        @Override
        public RemoteAddress get()
        {
            if (attempt > 0)
            {
                topologyManager.requestTopology();
            }

            attempt++;

            final ClusterStateImpl topology = topologyManager.getTopology();
            return addressStrategy.apply(topology);
        }
    }

    public static class ResponseFuture<E> implements ActorFuture<E>, ZeebeFuture<E>
    {
        protected final ActorFuture<ClientResponse> transportFuture;
        protected final RequestResponseHandler responseHandler;
        protected final ErrorResponseHandler errorHandler = new ErrorResponseHandler();
        protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
        protected final Duration requestTimeout;

        protected E result = null;
        protected ExecutionException failure = null;

        ResponseFuture(ActorFuture<ClientResponse> transportFuture,
                RequestResponseHandler responseHandler,
                Duration requestTimeout)
        {
            this.transportFuture = transportFuture;
            this.responseHandler = responseHandler;
            this.requestTimeout = requestTimeout;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isCancelled()
        {
            return false;
        }

        @Override
        public boolean isDone()
        {
            return transportFuture.isDone();
        }

        protected void ensureResponseAvailable(long timeout, TimeUnit unit)
        {
            if (result != null || failure != null)
            {
                return;
            }

            try
            {
                final ClientResponse response = transportFuture.get(timeout, unit);
                final DirectBuffer responsBuffer = response.getResponseBuffer();

                headerDecoder.wrap(responsBuffer, 0);

                if (responseHandler.handlesResponse(headerDecoder))
                {
                    handleExpectedResponse(response, responsBuffer);
                    return;
                }
                else if (errorHandler.handlesResponse(headerDecoder))
                {
                    handleErrorResponse(responsBuffer);
                    return;
                }
                else
                {
                    failWith("Unexpected response format");
                    return;
                }
            }
            catch (ExecutionException e)
            {
                if (e.getCause() != null && e.getCause() instanceof RequestTimeoutException)
                {
                    failWith("Request timed out (" + requestTimeout + ")", e);
                }
                else
                {
                    failWith("Could not complete request", e);
                }
                return;
            }
            catch (InterruptedException | TimeoutException e)
            {
                failWith("Could not complete request", e);
                return;
            }
        }

        private void handleErrorResponse(final DirectBuffer responseContent)
        {
            errorHandler.wrap(responseContent,
                    headerDecoder.encodedLength(),
                    headerDecoder.blockLength(),
                    headerDecoder.version());

            final ErrorCode errorCode = errorHandler.getErrorCode();

            if (errorCode != ErrorCode.NULL_VAL)
            {
                try
                {
                    final String errorMessage = BufferUtil.bufferAsString(errorHandler.getErrorMessage());
                    failWith(new BrokerErrorException(errorCode, errorMessage));
                    return;
                }
                catch (final Exception e)
                {
                    failWith(new BrokerErrorException(errorCode, "Unable to parse error message from response: " + e.getMessage()));
                    return;
                }
            }
            else
            {
                failWith("Unknown error during request execution");
                return;
            }
        }

        @SuppressWarnings("unchecked")
        private void handleExpectedResponse(final ClientResponse response, final DirectBuffer responseBuffer)
        {
            try
            {
                this.result = (E) responseHandler.getResult(responseBuffer,
                        headerDecoder.encodedLength(),
                        headerDecoder.blockLength(),
                        headerDecoder.version());

                if (this.result instanceof ReceiverAwareResponseResult)
                {
                    ((ReceiverAwareResponseResult) this.result).setReceiver(response.getRemoteAddress());
                }

                return;
            }
            catch (ClientCommandRejectedException e)
            {
                failWith(e);
                return;
            }
            catch (Exception e)
            {
                failWith("Unexpected exception during response handling", e);
                return;
            }
        }

        protected void failWith(Exception e)
        {
            this.failure = new ExecutionException(e);
        }

        protected void failWith(String message)
        {
            failWith(new ClientException(message + ". Request was: " + responseHandler.describeRequest()));
        }

        protected void failWith(String message, Throwable cause)
        {
            failWith(new ClientException(message + ". Request was: " + responseHandler.describeRequest(), cause));
        }

        @Override
        public E get() throws InterruptedException, ExecutionException
        {
            try
            {
                return get(1, TimeUnit.DAYS);
            }
            catch (TimeoutException e)
            {
                throw new ClientException("Failed to wait for response", e);
            }
        }

        @Override
        public E get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException
        {
            ensureResponseAvailable(timeout, unit);

            if (result != null)
            {
                return result;
            }
            else
            {
                throw failure;
            }
        }

        @Override
        public void complete(E value)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void completeExceptionally(String failure, Throwable throwable)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void completeExceptionally(Throwable throwable)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public E join()
        {
            try
            {
                return get();
            }
            catch (ExecutionException e)
            {
                final Throwable cause = e.getCause();

                if (cause instanceof ClientException)
                {
                    final ClientException clientException = (ClientException) cause;
                    throw clientException.newInCurrentContext();
                }
                else
                {
                    throw new ClientException(cause.getMessage(), cause);
                }
            }
            catch (InterruptedException e)
            {
                throw new ClientException(e.getMessage(), e);
            }
        }

        @Override
        public E join(long timeout, TimeUnit unit)
        {
            try
            {
                return get(timeout, unit);
            }
            catch (ExecutionException e)
            {
                final Throwable cause = e.getCause();

                if (cause instanceof ClientException)
                {
                    final ClientException clientException = (ClientException) cause;
                    throw clientException.newInCurrentContext();
                }
                else
                {
                    throw new ClientException(cause.getMessage(), cause);
                }
            }
            catch (InterruptedException | TimeoutException e)
            {
                throw new ClientException(e.getMessage(), e);
            }
        }

        @Override
        public void block(ActorTask onCompletion)
        {
            transportFuture.block(onCompletion);
        }

        @Override
        public boolean isCompletedExceptionally()
        {
            if (transportFuture.isDone())
            {
                ensureResponseAvailable(1, TimeUnit.SECONDS);
                return failure != null;
            }
            else
            {
                return false;
            }
        }

        @Override
        public Throwable getException()
        {
            return failure;
        }

    }

}
