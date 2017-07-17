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
package io.zeebe.client.clustering.impl;

import static io.zeebe.util.EnsureUtil.ensureNotNull;

import java.time.Duration;
import java.util.function.Consumer;

import org.agrona.DirectBuffer;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.zeebe.client.cmd.BrokerRequestException;
import io.zeebe.client.impl.cmd.ClientResponseHandler;
import io.zeebe.protocol.clientapi.ErrorResponseDecoder;
import io.zeebe.protocol.clientapi.MessageHeaderDecoder;
import io.zeebe.transport.ClientOutput;
import io.zeebe.transport.ClientRequest;
import io.zeebe.transport.ClientTransport;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.util.buffer.BufferWriter;
import io.zeebe.util.state.SimpleStateMachineContext;
import io.zeebe.util.state.State;
import io.zeebe.util.state.StateMachine;
import io.zeebe.util.state.WaitState;

public class ClientTopologyController
{
    public static final long REFRESH_INTERVAL = Duration.ofSeconds(10).toMillis();

    protected static final int TRANSITION_DEFAULT = 0;
    protected static final int TRANSITION_FAILED = 1;

    protected final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
    protected final ErrorResponseDecoder errorResponseDecoder = new ErrorResponseDecoder();

    protected final StateMachine<Context> stateMachine;
    protected final RequestTopologyState requestTopologyState = new RequestTopologyState();
    protected final AwaitTopologyState awaitTopologyState = new AwaitTopologyState();
    protected final InitState initState = new InitState();

    protected final RequestTopologyCmdImpl requestTopologyCmd;

    private final ClientOutput output;
    protected final Consumer<TopologyResponse> successCallback;
    protected final Consumer<Exception> failureCallback;
    protected final BufferWriter requestWriter;

    public ClientTopologyController(
            final ClientTransport clientTransport,
            final ObjectMapper objectMapper,
            final Consumer<TopologyResponse> successCallback,
            final Consumer<Exception> failureCallback)
    {
        output = clientTransport.getOutput();
        this.requestTopologyCmd = new RequestTopologyCmdImpl(null, objectMapper);

        stateMachine = StateMachine.builder(Context::new)
            .initialState(initState)
            .from(initState).take(TRANSITION_DEFAULT).to(requestTopologyState)
            .from(requestTopologyState).take(TRANSITION_DEFAULT).to(awaitTopologyState)
            .from(awaitTopologyState).take(TRANSITION_DEFAULT).to(initState)
            .build();

        this.successCallback = successCallback;
        this.failureCallback = failureCallback;
        this.requestWriter = requestTopologyCmd.getRequestWriter();
    }

    public ClientTopologyController triggerRefresh(final RemoteAddress socketAddress)
    {
        ensureNotNull("socketAddress", socketAddress);

        stateMachine.reset();

        final Context context = stateMachine.getContext();
        context.remoteAddress = socketAddress;

        stateMachine.take(TRANSITION_DEFAULT);

        return this;
    }

    public int doWork()
    {
        return stateMachine.doWork();
    }

    public boolean isRequestInProgress()
    {
        return stateMachine.getCurrentState() != initState;
    }

    private class RequestTopologyState implements State<Context>
    {

        @Override
        public int doWork(final Context context) throws Exception
        {
            final ClientRequest request = output.sendRequest(context.remoteAddress, requestWriter);
            if (request != null)
            {
                context.request = request;
                context.take(TRANSITION_DEFAULT);
            }

            return 1;
        }
    }

    private class AwaitTopologyState implements State<Context>
    {

        @Override
        public int doWork(final Context context) throws Exception
        {
            int workCount = 0;

            final ClientRequest request = context.request;

            if (request.isDone())
            {
                try
                {
                    final DirectBuffer response = request.get();
                    final TopologyResponse topologyResponse = decodeTopology(response);
                    successCallback.accept(topologyResponse);
                }
                catch (Exception e)
                {
                    failureCallback.accept(e);
                }
                finally
                {
                    context.take(TRANSITION_DEFAULT);
                    request.close();
                }

                workCount = 1;
            }

            return workCount;
        }
    }

    protected TopologyResponse decodeTopology(DirectBuffer encodedTopology)
    {
        messageHeaderDecoder.wrap(encodedTopology, 0);

        final int schemaId = messageHeaderDecoder.schemaId();
        final int templateId = messageHeaderDecoder.templateId();
        final int blockLength = messageHeaderDecoder.blockLength();
        final int version = messageHeaderDecoder.version();

        final int responseMessageOffset = messageHeaderDecoder.encodedLength();

        final ClientResponseHandler<TopologyResponse> responseHandler = requestTopologyCmd.getResponseHandler();

        if (schemaId == responseHandler.getResponseSchemaId() && templateId == responseHandler.getResponseTemplateId())
        {
            try
            {
                return responseHandler.readResponse(encodedTopology, responseMessageOffset, blockLength, version);
            }
            catch (final Exception e)
            {
                throw new RuntimeException("Unable to parse topic list from broker response", e);
            }
        }
        else
        {
            errorResponseDecoder.wrap(encodedTopology, 0, blockLength, version);
            throw new BrokerRequestException(errorResponseDecoder.errorCode(), errorResponseDecoder.errorData());
        }
    }

    private class InitState implements WaitState<Context>
    {
        @Override
        public void work(final Context context) throws Exception
        {
        }
    }

    static class Context extends SimpleStateMachineContext
    {
        ClientRequest request;

        // keep during reset to allow automatic refresh with last configuration
        RemoteAddress remoteAddress;

        Context(final StateMachine<?> stateMachine)
        {
            super(stateMachine);
        }

        @Override
        public void reset()
        {
            this.request = null;
            this.remoteAddress = null;
        }
    }

}
