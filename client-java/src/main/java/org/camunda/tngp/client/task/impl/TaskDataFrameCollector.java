package org.camunda.tngp.client.task.impl;

import org.agrona.DirectBuffer;
import org.camunda.tngp.client.impl.cmd.taskqueue.TaskEvent;
import org.camunda.tngp.dispatcher.FragmentHandler;
import org.camunda.tngp.dispatcher.Subscription;
import org.camunda.tngp.transport.protocol.Protocols;
import org.camunda.tngp.transport.protocol.TransportHeaderDescriptor;

public class TaskDataFrameCollector
{
    protected Subscription receiveBufferSubscription;

    protected SubscribedTaskHandler taskHandler;
    protected DataFrameHandler fragmentHandler = new DataFrameHandler();

    public TaskDataFrameCollector(Subscription receiveBufferSubscription)
    {
        this.receiveBufferSubscription = receiveBufferSubscription;
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
            final short protocolId = buffer.getShort(TransportHeaderDescriptor.protocolIdOffset(offset));

            if (protocolId == Protocols.FULL_DUPLEX_SINGLE_MESSAGE)
            {
                final int protocolHeaderLength = TransportHeaderDescriptor.headerLength();
                // TODO handle event - ensure that this is a task event
                //taskHandler.onTask();
            }

            return FragmentHandler.CONSUME_FRAGMENT_RESULT;
        }

    }

    public interface SubscribedTaskHandler
    {
        void onTask(TaskEvent task);
    }



}
