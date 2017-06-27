package io.zeebe.client.clustering.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.agrona.DirectBuffer;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.zeebe.client.clustering.Topology;
import io.zeebe.client.cmd.BrokerRequestException;
import io.zeebe.client.impl.Topic;
import io.zeebe.client.impl.cmd.ClientResponseHandler;
import io.zeebe.protocol.clientapi.ErrorResponseDecoder;
import io.zeebe.protocol.clientapi.MessageHeaderDecoder;
import io.zeebe.transport.ClientTransport;
import io.zeebe.transport.SocketAddress;
import io.zeebe.util.DeferredCommandContext;
import io.zeebe.util.actor.Actor;
import io.zeebe.util.buffer.BufferReader;


public class ClientTopologyManager implements Actor, BufferReader
{

    protected final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
    protected final ErrorResponseDecoder errorResponseDecoder = new ErrorResponseDecoder();

    protected final DeferredCommandContext commandContext = new DeferredCommandContext();

    protected final RequestTopologyCmdImpl requestTopologyCmd;
    protected final ClientTopologyController clientTopologyController;
    protected final List<CompletableFuture<Void>> refreshFutures;

    protected Topology topology;
    protected CompletableFuture<Void> refreshFuture;

    public ClientTopologyManager(final ClientTransport transport, final ObjectMapper objectMapper, final SocketAddress... initialBrokers)
    {
        this.clientTopologyController = new ClientTopologyController(transport);
        this.requestTopologyCmd = new RequestTopologyCmdImpl(null, objectMapper);
        this.topology = new TopologyImpl(initialBrokers);

        this.refreshFutures = new ArrayList<>();
        triggerRefresh();
    }

    @Override
    public int doWork() throws Exception
    {
        int workCount = 0;

        workCount += commandContext.doWork();
        workCount += clientTopologyController.doWork();

        return workCount;
    }

    public Topology getTopology()
    {
        return topology;
    }

    public SocketAddress getLeaderForTopic(final Topic topic)
    {
        if (topic != null)
        {
            return topology.getLeaderForTopic(topic);
        }
        else
        {
            return topology.getRandomBroker();
        }
    }

    public CompletableFuture<Void> refreshNow()
    {
        return commandContext.runAsync(future ->
        {
            if (clientTopologyController.isIdle())
            {
                triggerRefresh();
            }

            refreshFutures.add(future);

        });
    }

    protected void triggerRefresh()
    {
        refreshFuture = new CompletableFuture<>();

        refreshFuture.handle((value, throwable) ->
        {
            if (throwable == null)
            {
                refreshFutures.forEach(f -> f.complete(value));
            }
            else
            {
                refreshFutures.forEach(f -> f.completeExceptionally(throwable));
            }

            refreshFutures.clear();

            return null;
        });

        clientTopologyController.configure(topology.getRandomBroker(), requestTopologyCmd.getRequestWriter(), this, refreshFuture);
    }

    @Override
    public void wrap(final DirectBuffer buffer, final int offset, final int length)
    {
        messageHeaderDecoder.wrap(buffer, 0);

        final int schemaId = messageHeaderDecoder.schemaId();
        final int templateId = messageHeaderDecoder.templateId();
        final int blockLength = messageHeaderDecoder.blockLength();
        final int version = messageHeaderDecoder.version();

        final int responseMessageOffset = messageHeaderDecoder.encodedLength();

        final ClientResponseHandler<Topology> responseHandler = requestTopologyCmd.getResponseHandler();

        if (schemaId == responseHandler.getResponseSchemaId() && templateId == responseHandler.getResponseTemplateId())
        {
            try
            {
                topology = responseHandler.readResponse(buffer, responseMessageOffset, blockLength, version);
            }
            catch (final Exception e)
            {
                throw new RuntimeException("Unable to parse topic list from broker response", e);
            }
        }
        else
        {
            errorResponseDecoder.wrap(buffer, offset, blockLength, version);
            throw new BrokerRequestException(errorResponseDecoder.errorCode(), errorResponseDecoder.errorData());
        }

    }

}
