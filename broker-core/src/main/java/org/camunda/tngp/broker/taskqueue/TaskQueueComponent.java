package org.camunda.tngp.broker.taskqueue;

import static org.camunda.tngp.broker.system.SystemServiceNames.AGENT_RUNNER_SERVICE;
import static org.camunda.tngp.broker.taskqueue.TaskQueueServiceNames.TASK_QUEUE_MANAGER;
import static org.camunda.tngp.broker.taskqueue.TaskQueueServiceNames.TASK_QUEUE_SUBSCRIPTION_MANAGER;
import static org.camunda.tngp.broker.transport.TransportServiceNames.TRANSPORT_SEND_BUFFER;

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

        final TaskQueueManagerService taskQueueManagerService = new TaskQueueManagerService(configurationManager);
        serviceContainer.createService(TASK_QUEUE_MANAGER, taskQueueManagerService)
            .dependency(TRANSPORT_SEND_BUFFER, taskQueueManagerService.getSendBufferInjector())
            .install();

        final TaskSubscriptionManagerService taskSubscriptionManagerService = new TaskSubscriptionManagerService();
        serviceContainer.createService(TASK_QUEUE_SUBSCRIPTION_MANAGER, taskSubscriptionManagerService)
            .dependency(AGENT_RUNNER_SERVICE, taskSubscriptionManagerService.getAgentRunnerServicesInjector())
            .install();
    }

}
