package org.camunda.tngp.broker.taskqueue;

import java.util.List;

import org.camunda.tngp.broker.log.LogConsumer;
import org.camunda.tngp.broker.taskqueue.cfg.TaskQueueCfg;
import org.camunda.tngp.broker.transport.worker.spi.ResourceContextProvider;

public interface TaskQueueManager extends ResourceContextProvider<TaskQueueContext>
{
    void startTaskQueue(TaskQueueCfg taskQueueCfg);

    List<LogConsumer> getInputLogConsumers();

    void registerInputLogConsumer(LogConsumer logConsumer);
}
