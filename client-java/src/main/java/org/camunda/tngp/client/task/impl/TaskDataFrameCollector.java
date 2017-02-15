package org.camunda.tngp.client.task.impl;

import java.io.IOException;

import org.agrona.DirectBuffer;
import org.camunda.tngp.client.impl.cmd.taskqueue.TaskEvent;
import org.camunda.tngp.dispatcher.FragmentHandler;
import org.camunda.tngp.dispatcher.Subscription;
import org.camunda.tngp.protocol.clientapi.MessageHeaderDecoder;
import org.camunda.tngp.protocol.clientapi.SubscribedEventDecoder;
import org.camunda.tngp.protocol.clientapi.SubscriptionType;
import org.camunda.tngp.transport.protocol.Protocols;
import org.camunda.tngp.transport.protocol.TransportHeaderDescriptor;

import com.fasterxml.jackson.databind.ObjectMapper;

public class TaskDataFrameCollector
{
    protected final TransportHeaderDescriptor transportHeaderDescriptor = new TransportHeaderDescriptor();
    protected final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
    protected final SubscribedEventDecoder subscribedEventDecoder = new SubscribedEventDecoder();

    protected final Subscription receiveBufferSubscription;
    protected final ObjectMapper objectMapper;

    protected SubscribedTaskHandler taskHandler;
    protected DataFrameHandler fragmentHandler = new DataFrameHandler();

    public TaskDataFrameCollector(Subscription receiveBufferSubscription, ObjectMapper objectMapper)
    {
        this.receiveBufferSubscription = receiveBufferSubscription;
        this.objectMapper = objectMapper;
    }

    public void setTaskHandler(SubscribedTaskHandler taskHandler)
    {
        this.taskHandler = taskHandler;
    }

    public int doWork()
    {
        return receiveBufferSubscription.poll(fragmentHandler, Integer.MAX_VALUE);
    }

    public class DataFrameHandler implements FragmentHandler
    {

        @Override
        public int onFragment(DirectBuffer buffer, int offset, int length, int streamId, boolean isMarkedFailed)
        {
            transportHeaderDescriptor.wrap(buffer, offset);

            offset += TransportHeaderDescriptor.HEADER_LENGTH;

            messageHeaderDecoder.wrap(buffer, offset);

            offset += MessageHeaderDecoder.ENCODED_LENGTH;

            final int protocolId = transportHeaderDescriptor.protocolId();
            final int templateId = messageHeaderDecoder.templateId();

            if (protocolId == Protocols.FULL_DUPLEX_SINGLE_MESSAGE && templateId == SubscribedEventDecoder.TEMPLATE_ID)
            {
                subscribedEventDecoder.wrap(buffer, offset, messageHeaderDecoder.blockLength(), messageHeaderDecoder.version());

                final SubscriptionType subscriptionType = subscribedEventDecoder.subscriptionType();

                if (subscriptionType == SubscriptionType.TASK_SUBSCRIPTION)
                {
                    final long key = subscribedEventDecoder.longKey();
                    final long subscriptionId = subscribedEventDecoder.subscriptionId();

                    final byte[] eventBuffer = new byte[subscribedEventDecoder.eventLength()];
                    subscribedEventDecoder.getEvent(eventBuffer, 0, eventBuffer.length);

                    try
                    {
                        final TaskEvent taskEvent = objectMapper.readValue(eventBuffer, TaskEvent.class);

                        taskHandler.onTask(subscriptionId, key, taskEvent);
                    }
                    catch (IOException e)
                    {
                        throw new RuntimeException("Failed to deserialize task event", e);
                    }
                }
            }

            return FragmentHandler.CONSUME_FRAGMENT_RESULT;
        }

    }

    public interface SubscribedTaskHandler
    {
        void onTask(long subscriptionId, long key, TaskEvent task);
    }

}
