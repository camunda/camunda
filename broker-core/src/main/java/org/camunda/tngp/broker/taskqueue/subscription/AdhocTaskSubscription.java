package org.camunda.tngp.broker.taskqueue.subscription;

import org.camunda.tngp.broker.taskqueue.LockedTaskBatchWriter;
import org.camunda.tngp.protocol.taskqueue.LockedTaskWriter;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponse;

public class AdhocTaskSubscription extends TaskSubscription
{

    protected long defaultCredits;
    protected DeferredResponse response;
    protected LockedTaskBatchWriter taskBatchResponseWriter = new LockedTaskBatchWriter();

    public AdhocTaskSubscription(long id)
    {
        this.id = id;
    }

    public void wrap(DeferredResponse response)
    {
        this.response = response;
    }

    public void setDefaultCredits(long defaultCredits)
    {
        this.defaultCredits = defaultCredits;
    }

    @Override
    public void onTaskLocked(LockTasksOperator taskOperator, LockedTaskWriter task)
    {
        taskBatchResponseWriter
            .consumerId(consumerId)
            .newTasks()
                .appendTask(task);

        response.allocateAndWrite(taskBatchResponseWriter);
        response.commit();

        taskOperator.removeSubscription(this);

    }

    public void onTaskAcquisitionFinished(LockTasksOperator taskOperator)
    {
        final boolean noTasksAcquired = defaultCredits == credits;

        if (noTasksAcquired)
        {
            taskBatchResponseWriter.consumerId(consumerId);

            response.allocateAndWrite(taskBatchResponseWriter);
            response.commit();

            taskOperator.removeSubscription(this);
        }
    }

    @Override
    public int getChannelId()
    {
        return response.getChannelId();
    }
}
