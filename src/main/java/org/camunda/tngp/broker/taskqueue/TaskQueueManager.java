package org.camunda.tngp.broker.taskqueue;

import org.camunda.tngp.broker.taskqueue.cfg.TaskQueueCfg;
import org.camunda.tngp.broker.transport.worker.spi.ResourceContextProvider;

public interface TaskQueueManager extends ResourceContextProvider<TaskQueueContext>
{
    void startTaskQueue(TaskQueueCfg taskQueueCfg);

    TaskQueueContext[] getTaskQueueContexts();
}
