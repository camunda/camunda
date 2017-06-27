package io.zeebe.broker.task;

import static io.zeebe.broker.logstreams.LogStreamServiceNames.LOG_STREAM_SERVICE_GROUP;
import static io.zeebe.broker.system.SystemServiceNames.ACTOR_SCHEDULER_SERVICE;
import static io.zeebe.broker.system.SystemServiceNames.EXECUTOR_SERVICE;
import static io.zeebe.broker.task.TaskQueueServiceNames.TASK_QUEUE_MANAGER;
import static io.zeebe.broker.task.TaskQueueServiceNames.TASK_QUEUE_SUBSCRIPTION_MANAGER;
import static io.zeebe.broker.transport.TransportServiceNames.CLIENT_API_SERVER_NAME;

import io.zeebe.broker.system.Component;
import io.zeebe.broker.system.ConfigurationManager;
import io.zeebe.broker.system.SystemContext;
import io.zeebe.broker.transport.TransportServiceNames;
import io.zeebe.servicecontainer.ServiceContainer;

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
            .dependency(TransportServiceNames.serverTransport(TransportServiceNames.CLIENT_API_SERVER_NAME), taskSubscriptionManagerService.getClientApiTransportInjector())
            .groupReference(LOG_STREAM_SERVICE_GROUP, taskSubscriptionManagerService.getLogStreamsGroupReference())
            .install();

        final TaskQueueManagerService taskQueueManagerService = new TaskQueueManagerService(configurationManager);
        serviceContainer.createService(TASK_QUEUE_MANAGER, taskQueueManagerService)
            .dependency(TransportServiceNames.serverTransport(CLIENT_API_SERVER_NAME), taskQueueManagerService.getClientApiTransportInjector())
            .dependency(EXECUTOR_SERVICE, taskQueueManagerService.getExecutorInjector())
            .dependency(TASK_QUEUE_SUBSCRIPTION_MANAGER, taskQueueManagerService.getTaskSubscriptionManagerInjector())
            .dependency(ACTOR_SCHEDULER_SERVICE, taskQueueManagerService.getActorSchedulerInjector())
            .groupReference(LOG_STREAM_SERVICE_GROUP, taskQueueManagerService.getLogStreamsGroupReference())
            .install();

    }

}
