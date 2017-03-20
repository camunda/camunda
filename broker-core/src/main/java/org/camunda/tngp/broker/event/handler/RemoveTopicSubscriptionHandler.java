package org.camunda.tngp.broker.event.handler;

import java.util.concurrent.CompletableFuture;

import org.agrona.DirectBuffer;
import org.camunda.tngp.broker.event.processor.CloseSubscriptionRequest;
import org.camunda.tngp.broker.event.processor.TopicSubscriptionService;
import org.camunda.tngp.broker.logstreams.BrokerEventMetadata;
import org.camunda.tngp.broker.transport.clientapi.ErrorResponseWriter;
import org.camunda.tngp.broker.transport.controlmessage.ControlMessageHandler;
import org.camunda.tngp.broker.transport.controlmessage.ControlMessageResponseWriter;
import org.camunda.tngp.protocol.clientapi.ControlMessageType;
import org.camunda.tngp.protocol.clientapi.ErrorCode;

public class RemoveTopicSubscriptionHandler implements ControlMessageHandler
{

    protected final CloseSubscriptionRequest request = new CloseSubscriptionRequest();

    protected final TopicSubscriptionService subscriptionService;
    protected final ControlMessageResponseWriter responseWriter;
    protected final ErrorResponseWriter errorResponseWriter;

    public RemoveTopicSubscriptionHandler(
            TopicSubscriptionService subscriptionService,
            ControlMessageResponseWriter responseWriter,
            ErrorResponseWriter errorResponseWriter)
    {
        this.subscriptionService = subscriptionService;
        this.responseWriter = responseWriter;
        this.errorResponseWriter = errorResponseWriter;
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

        final CompletableFuture<Void> future = subscriptionService.closeSubscriptionAsync(request.getTopicId(), request.getSubscriptionId());

        return future.handle((v, failure) ->
        {
            if (failure == null)
            {
                responseWriter
                    .brokerEventMetadata(metadata)
                    .dataWriter(request)
                    .tryWriteResponse();
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
