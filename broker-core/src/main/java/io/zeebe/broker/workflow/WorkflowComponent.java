package io.zeebe.broker.workflow;

import static io.zeebe.broker.logstreams.LogStreamServiceNames.LOG_STREAM_SERVICE_GROUP;
import static io.zeebe.broker.system.SystemServiceNames.ACTOR_SCHEDULER_SERVICE;
import static io.zeebe.broker.transport.TransportServiceNames.TRANSPORT_SEND_BUFFER;
import static io.zeebe.broker.workflow.WorkflowQueueServiceNames.WORKFLOW_QUEUE_MANAGER;

import io.zeebe.broker.system.Component;
import io.zeebe.broker.system.ConfigurationManager;
import io.zeebe.broker.system.SystemContext;
import io.zeebe.servicecontainer.ServiceContainer;

public class WorkflowComponent implements Component
{

    @Override
    public void init(SystemContext context)
    {
        final ServiceContainer serviceContainer = context.getServiceContainer();
        final ConfigurationManager configurationManager = context.getConfigurationManager();

        final WorkflowQueueManagerService workflowQueueManagerService = new WorkflowQueueManagerService(configurationManager);
        serviceContainer.createService(WORKFLOW_QUEUE_MANAGER, workflowQueueManagerService)
            .dependency(TRANSPORT_SEND_BUFFER, workflowQueueManagerService.getSendBufferInjector())
            .dependency(ACTOR_SCHEDULER_SERVICE, workflowQueueManagerService.getActorSchedulerInjector())
            .groupReference(LOG_STREAM_SERVICE_GROUP, workflowQueueManagerService.getLogStreamsGroupReference())
            .install();
    }

}
