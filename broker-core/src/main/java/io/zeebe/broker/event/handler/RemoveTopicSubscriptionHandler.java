package io.zeebe.broker.event.handler;

import java.util.concurrent.CompletableFuture;

import org.agrona.DirectBuffer;

import io.zeebe.broker.event.processor.CloseSubscriptionRequest;
import io.zeebe.broker.event.processor.TopicSubscriptionService;
import io.zeebe.broker.logstreams.BrokerEventMetadata;
import io.zeebe.broker.transport.clientapi.ErrorResponseWriter;
import io.zeebe.broker.transport.controlmessage.ControlMessageHandler;
import io.zeebe.broker.transport.controlmessage.ControlMessageResponseWriter;
import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.protocol.clientapi.ErrorCode;
import io.zeebe.transport.ServerOutput;

public class RemoveTopicSubscriptionHandler implements ControlMessageHandler
{

    protected final CloseSubscriptionRequest request = new CloseSubscriptionRequest();

    protected final TopicSubscriptionService subscriptionService;
    protected final ControlMessageResponseWriter responseWriter;
    protected final ErrorResponseWriter errorResponseWriter;

    public RemoveTopicSubscriptionHandler(ServerOutput output, TopicSubscriptionService subscriptionService)
    {
        this.errorResponseWriter = new ErrorResponseWriter(output);
        this.responseWriter = new ControlMessageResponseWriter(output);
        this.subscriptionService = subscriptionService;
    }

    @Override
    public ControlMessageType getMessageType()
    {
        return ControlMessageType.REMOVE_TOPIC_SUBSCRIPTION;
    }

    @Override
    public CompletableFuture<Void> handle(DirectBuffer buffer, BrokerEventMetadata metadata)
    {
        request.reset();
        request.wrap(buffer);

        final CompletableFuture<Void> future = subscriptionService.closeSubscriptionAsync(
            request.getTopicName(),
            request.getPartitionId(),
            request.getSubscriberKey()
        );

        return future.handle((v, failure) ->
        {
            if (failure == null)
            {
                final boolean success = responseWriter
                    .dataWriter(request)
                    .tryWriteResponse(metadata.getRequestStreamId(), metadata.getRequestId());
                // TODO: proper backpressure
            }
            else
            {
                final boolean success = errorResponseWriter
                    .errorCode(ErrorCode.REQUEST_PROCESSING_FAILURE)
                    .errorMessage("Cannot close topic subscription. %s", failure.getMessage())
                    .failedRequest(buffer, 0, buffer.capacity())
                    .tryWriteResponseOrLogFailure(metadata.getRequestStreamId(), metadata.getRequestId());
                // TODO: proper backpressure
            }
            return null;
        });
    }


}
