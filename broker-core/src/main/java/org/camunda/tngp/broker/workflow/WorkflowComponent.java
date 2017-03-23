package org.camunda.tngp.broker.workflow;

import static org.camunda.tngp.broker.logstreams.LogStreamServiceNames.LOG_STREAM_SERVICE_GROUP;
import static org.camunda.tngp.broker.system.SystemServiceNames.AGENT_RUNNER_SERVICE;
import static org.camunda.tngp.broker.transport.TransportServiceNames.TRANSPORT_SEND_BUFFER;
import static org.camunda.tngp.broker.workflow.WorkflowQueueServiceNames.WORKFLOW_QUEUE_MANAGER;

import org.camunda.tngp.broker.system.Component;
import org.camunda.tngp.broker.system.ConfigurationManager;
import org.camunda.tngp.broker.system.SystemContext;
import org.camunda.tngp.servicecontainer.ServiceContainer;

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
            .dependency(AGENT_RUNNER_SERVICE, workflowQueueManagerService.getAgentRunnerServicesInjector())
            .groupReference(LOG_STREAM_SERVICE_GROUP, workflowQueueManagerService.getLogStreamsGroupReference())
            .install();
    }

}
