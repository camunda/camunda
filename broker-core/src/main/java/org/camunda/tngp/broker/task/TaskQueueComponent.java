package org.camunda.tngp.broker.task;

import static org.camunda.tngp.broker.logstreams.LogStreamServiceNames.*;
import static org.camunda.tngp.broker.system.SystemServiceNames.*;
import static org.camunda.tngp.broker.task.TaskQueueServiceNames.*;
import static org.camunda.tngp.broker.transport.TransportServiceNames.*;

import org.camunda.tngp.broker.system.Component;
import org.camunda.tngp.broker.system.ConfigurationManager;
import org.camunda.tngp.broker.system.SystemContext;
import org.camunda.tngp.servicecontainer.ServiceContainer;

public class TaskQueueComponent implements Component
{

    @Override
    public void init(SystemContext context)
    {
        final ServiceContainer serviceContainer = context.getServiceContainer();
        final ConfigurationManager configurationManager = context.getConfigurationManager();

        final TaskSubscriptionManagerService taskSubscriptionManagerService = new TaskSubscriptionManagerService();
        serviceContainer.createService(TASK_QUEUE_SUBSCRIPTION_MANAGER, taskSubscriptionManagerService)
            .dependency(ACTOR_SCHEDULER_SERVICE, taskSubscriptionManagerService.getActorSchedulerInjector())
            .groupReference(LOG_STREAM_SERVICE_GROUP, taskSubscriptionManagerService.getLogStreamsGroupReference())
            .install();

        final TaskQueueManagerService taskQueueManagerService = new TaskQueueManagerService(configurationManager);
        serviceContainer.createService(TASK_QUEUE_MANAGER, taskQueueManagerService)
            .dependency(TRANSPORT_SEND_BUFFER, taskQueueManagerService.getSendBufferInjector())
            .dependency(EXECUTOR_SERVICE, taskQueueManagerService.getExecutorInjector())
            .dependency(TASK_QUEUE_SUBSCRIPTION_MANAGER, taskQueueManagerService.getTaskSubscriptionManagerInjector())
            .dependency(ACTOR_SCHEDULER_SERVICE, taskQueueManagerService.getActorSchedulerInjector())
            .groupReference(LOG_STREAM_SERVICE_GROUP, taskQueueManagerService.getLogStreamsGroupReference())
            .install();

    }

}
