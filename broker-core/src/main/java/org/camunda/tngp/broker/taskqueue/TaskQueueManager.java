package org.camunda.tngp.broker.taskqueue;

import java.util.List;

import org.camunda.tngp.broker.services.LogEntryProcessorService.LogEntryProcessor;
import org.camunda.tngp.broker.taskqueue.cfg.TaskQueueCfg;
import org.camunda.tngp.broker.transport.worker.spi.ResourceContextProvider;

public interface TaskQueueManager extends ResourceContextProvider<TaskQueueContext>
{
    void startTaskQueue(TaskQueueCfg taskQueueCfg);

    List<LogEntryProcessor<?>> getInputLogProcessors();

    void registerInputLogProcessor(LogEntryProcessor<?> logReadHandler);
}
