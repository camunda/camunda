package io.zeebe.broker.clustering.handler;

import java.util.concurrent.CompletableFuture;

import io.zeebe.broker.clustering.gossip.Gossip;
import io.zeebe.broker.logstreams.BrokerEventMetadata;
import io.zeebe.broker.transport.clientapi.ErrorResponseWriter;
import io.zeebe.broker.transport.controlmessage.ControlMessageHandler;
import io.zeebe.broker.transport.controlmessage.ControlMessageResponseWriter;
import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.protocol.clientapi.ErrorCode;
import org.agrona.DirectBuffer;

public class RequestTopologyHandler implements ControlMessageHandler
{

    protected final Gossip gossip;
    protected final ControlMessageResponseWriter responseWriter;
    protected final ErrorResponseWriter errorResponseWriter;

    public RequestTopologyHandler(final Gossip gossip, final ControlMessageResponseWriter responseWriter, final ErrorResponseWriter errorResponseWriter)
    {
        this.gossip = gossip;
        this.responseWriter = responseWriter;
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    public ControlMessageType getMessageType()
    {
        return ControlMessageType.REQUEST_TOPOLOGY;
    }

    @Override
    public CompletableFuture<Void> handle(final DirectBuffer buffer, final BrokerEventMetadata metadata)
    {
        return gossip.getTopology()
            .handle((topology, failure) ->
            {
                if (failure == null)
                {
                    responseWriter
                        .brokerEventMetadata(metadata)
                        .dataWriter(topology);

                    if (!responseWriter.tryWriteResponse())
                    {
                        errorResponseWriter
                            .metadata(metadata)
                            .errorCode(ErrorCode.REQUEST_WRITE_FAILURE)
                            .errorMessage("Cannot write topology response.")
                            .failedRequest(buffer, 0, buffer.capacity())
                            .tryWriteResponseOrLogFailure();
                    }
                }
                else
                {
                    errorResponseWriter
                        .metadata(metadata)
                        .errorCode(ErrorCode.REQUEST_PROCESSING_FAILURE)
                        .errorMessage("Cannot close topic subscription. %s", failure.getMessage())
                        .failedRequest(buffer, 0, buffer.capacity())
                        .tryWriteResponseOrLogFailure();
                }

                return null;
            });

    }

}
