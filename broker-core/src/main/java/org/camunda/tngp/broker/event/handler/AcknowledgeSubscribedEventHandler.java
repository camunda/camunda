package org.camunda.tngp.broker.event.handler;

import java.util.concurrent.CompletableFuture;

import org.agrona.DirectBuffer;
import org.camunda.tngp.broker.event.processor.SubscriptionAcknowledgement;
import org.camunda.tngp.broker.event.processor.TopicSubscriptionManager;
import org.camunda.tngp.broker.logstreams.BrokerEventMetadata;
import org.camunda.tngp.broker.transport.clientapi.ErrorResponseWriter;
import org.camunda.tngp.broker.transport.controlmessage.ControlMessageHandler;
import org.camunda.tngp.broker.transport.controlmessage.ControlMessageResponseWriter;
import org.camunda.tngp.protocol.clientapi.ControlMessageType;
import org.camunda.tngp.protocol.clientapi.ErrorCode;

public class AcknowledgeSubscribedEventHandler implements ControlMessageHandler
{
    protected final SubscriptionAcknowledgement ack = new SubscriptionAcknowledgement();

    protected final TopicSubscriptionManager subscriptionManager;
    protected final ControlMessageResponseWriter responseWriter;
    protected final ErrorResponseWriter errorResponseWriter;

    public AcknowledgeSubscribedEventHandler(
            TopicSubscriptionManager subscriptionManager,
            ControlMessageResponseWriter responseWriter,
            ErrorResponseWriter errorResponseWriter)
    {
        this.subscriptionManager = subscriptionManager;
        this.responseWriter = responseWriter;
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    public ControlMessageType getMessageType()
    {
        return ControlMessageType.ACKNOWLEDGE_TOPIC_EVENT;
    }

    @Override
    public CompletableFuture<Void> handle(DirectBuffer buffer, BrokerEventMetadata metadata)
    {
        ack.reset();
        ack.wrap(buffer);

        final CompletableFuture<Void> future = subscriptionManager.submitAcknowledgedPosition(ack.getSubscriptionId(), ack.getAcknowledgedPosition());

        return future.handle((v, failure) ->
        {
            if (failure == null)
            {
                responseWriter
                    .brokerEventMetadata(metadata)
                    .dataWriter(ack)
                    .tryWriteResponse();
            }
            else
            {
                errorResponseWriter
                    .metadata(metadata)
                    .errorCode(ErrorCode.REQUEST_PROCESSING_FAILURE)
                    .errorMessage("Cannot acknowledge last processed event. %s", failure.getMessage())
                    .failedRequest(buffer, 0, buffer.capacity())
                    .tryWriteResponseOrLogFailure();
            }
            return null;
        });
    }

}
