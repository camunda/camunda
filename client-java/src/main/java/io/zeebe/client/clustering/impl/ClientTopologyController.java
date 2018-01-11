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

import java.util.function.Consumer;

import org.agrona.DirectBuffer;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.zeebe.client.cmd.BrokerErrorException;
import io.zeebe.client.impl.ControlMessageRequestHandler;
import io.zeebe.client.impl.Loggers;
import io.zeebe.protocol.clientapi.ErrorResponseDecoder;
import io.zeebe.protocol.clientapi.MessageHeaderDecoder;
import io.zeebe.transport.ClientOutput;
import io.zeebe.transport.ClientRequest;
import io.zeebe.transport.ClientTransport;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.util.state.SimpleStateMachineContext;
import io.zeebe.util.state.State;
import io.zeebe.util.state.StateMachine;
import io.zeebe.util.state.WaitState;

public class ClientTopologyController
{

    protected static final int TRANSITION_DEFAULT = 0;
    private static final int REQUEST_TIMEOUT_MS = 1000; // this should not be a large value
    // to avoid constantly requesting the topology from an unavailable broker

    protected final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
    protected final ErrorResponseDecoder errorResponseDecoder = new ErrorResponseDecoder();

    protected final StateMachine<Context> stateMachine;
    protected final RequestTopologyState requestTopologyState = new RequestTopologyState();
    protected final AwaitTopologyState awaitTopologyState = new AwaitTopologyState();
    protected final InitState initState = new InitState();

    private final ClientOutput output;
    protected final Consumer<TopologyResponse> successCallback;
    protected final Consumer<Exception> failureCallback;

    protected final ControlMessageRequestHandler requestHandler;

    public ClientTopologyController(
            final ClientTransport clientTransport,
            final ObjectMapper objectMapper,
            final Consumer<TopologyResponse> successCallback,
            final Consumer<Exception> failureCallback)
    {
        output = clientTransport.getOutput();
        this.requestHandler = new ControlMessageRequestHandler(objectMapper);
        requestHandler.configure(new RequestTopologyCmdImpl(null));

        stateMachine = StateMachine.builder(Context::new)
            .initialState(initState)
            .from(initState).take(TRANSITION_DEFAULT).to(requestTopologyState)
            .from(requestTopologyState).take(TRANSITION_DEFAULT).to(awaitTopologyState)
            .from(awaitTopologyState).take(TRANSITION_DEFAULT).to(initState)
            .build();

        this.successCallback = successCallback;
        this.failureCallback = failureCallback;
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
            int workCount = 0;

            final ClientRequest request = output.sendRequestWithRetry(context.remoteAddress, requestHandler, REQUEST_TIMEOUT_MS);
            if (request != null)
            {
                workCount++;
                context.request = request;
                context.take(TRANSITION_DEFAULT);
            }

            return workCount;
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
                workCount++;

                try
                {
                    final DirectBuffer response = request.get();
                    final TopologyResponse topologyResponse = decodeTopology(response);

                    successCallback.accept(topologyResponse);
                }
                catch (Exception e)
                {
                    Loggers.CLIENT_LOGGER.debug("Topology request failed", e);
                    failureCallback.accept(e);
                }
                finally
                {
                    context.take(TRANSITION_DEFAULT);
                    request.close();
                }
            }

            return workCount;
        }
    }

    protected TopologyResponse decodeTopology(DirectBuffer encodedTopology)
    {
        messageHeaderDecoder.wrap(encodedTopology, 0);

        final int blockLength = messageHeaderDecoder.blockLength();
        final int version = messageHeaderDecoder.version();

        final int responseMessageOffset = messageHeaderDecoder.encodedLength();

        if (requestHandler.handlesResponse(messageHeaderDecoder))
        {
            try
            {
                return (TopologyResponse) requestHandler.getResult(encodedTopology, responseMessageOffset, blockLength, version);
            }
            catch (final Exception e)
            {
                throw new RuntimeException("Unable to parse topic list from broker response", e);
            }
        }
        else if (messageHeaderDecoder.schemaId() == ErrorResponseDecoder.SCHEMA_ID && messageHeaderDecoder.templateId() == ErrorResponseDecoder.TEMPLATE_ID)
        {
            errorResponseDecoder.wrap(encodedTopology, 0, blockLength, version);
            throw new BrokerErrorException(errorResponseDecoder.errorCode(), errorResponseDecoder.errorData());
        }
        else
        {
            throw new RuntimeException(String.format("Unexpected response format. Schema %s and template %s.", messageHeaderDecoder.schemaId(), messageHeaderDecoder.templateId()));
        }
    }

    private static class InitState implements WaitState<Context>
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
