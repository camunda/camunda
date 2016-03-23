package org.camunda.tngp.taskqueue;

import org.camunda.tngp.taskqueue.protocol.CreateTaskInstanceHandler;
import org.camunda.tngp.transport.protocol.async.AsyncProtocolWorker;

public class TaskQueueWorker extends AsyncProtocolWorker
{

    public TaskQueueWorker(TaskQueueContext taskQueueContext)
    {
        super(taskQueueContext);

        // register handlers
        registerHandler(new CreateTaskInstanceHandler(taskQueueContext));
    }

    @Override
    public String roleName()
    {
        return "task-queue-worker";
    }

}
