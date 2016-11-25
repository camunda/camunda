package org.camunda.tngp.broker.taskqueue;

import org.camunda.tngp.broker.system.Component;
import org.camunda.tngp.broker.system.ConfigurationManager;
import org.camunda.tngp.broker.system.SystemContext;
import org.camunda.tngp.broker.taskqueue.cfg.TaskQueueComponentCfg;
import org.camunda.tngp.servicecontainer.ServiceContainer;

public class TaskQueueComponent implements Component
{
    public static final String WORKER_NAME = "task-queue-worker.0";

    @Override
    public void init(SystemContext context)
    {
        final ServiceContainer serviceContainer = context.getServiceContainer();
        final ConfigurationManager configurationManager = context.getConfigurationManager();
        final TaskQueueComponentCfg cfg = configurationManager.readEntry("task-queues", TaskQueueComponentCfg.class);
    }

}
