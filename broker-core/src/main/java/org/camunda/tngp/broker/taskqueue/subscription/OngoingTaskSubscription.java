package org.camunda.tngp.broker.taskqueue.subscription;

import org.camunda.tngp.protocol.taskqueue.LockedTaskWriter;
import org.camunda.tngp.protocol.taskqueue.SubscribedTaskWriter;
import org.camunda.tngp.transport.singlemessage.DataFramePool;
import org.camunda.tngp.transport.singlemessage.OutgoingDataFrame;

public class OngoingTaskSubscription extends TaskSubscription
{

    protected int channelId;
    protected DataFramePool dataFramePool;

    protected SubscribedTaskWriter subscribedTaskWriter = new SubscribedTaskWriter();

    /**
     * Creates a long running subscription
     */
    public OngoingTaskSubscription(long id, int channelId, DataFramePool dataFramePool)
    {
        this.id = id;
        this.channelId = channelId;
        this.dataFramePool = dataFramePool;
    }

    @Override
    public void onTaskLocked(LockTasksOperator taskOperator, LockedTaskWriter task)
    {
        subscribedTaskWriter
            .subscriptionId(id)
            .task(task);

        final OutgoingDataFrame dataFrame = dataFramePool.openFrame(
                subscribedTaskWriter.getLength(),
                channelId);

        dataFrame.write(subscribedTaskWriter);

        dataFrame.commit();
    }

    @Override
    public void onTaskAcquisitionFinished(LockTasksOperator taskOperator)
    {
    }

    @Override
    public int getChannelId()
    {
        return channelId;
    }

}
