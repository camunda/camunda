package org.camunda.tngp.broker.wf;

import static org.camunda.tngp.broker.system.SystemServiceNames.*;
import static org.camunda.tngp.broker.transport.TransportServiceNames.*;
import static org.camunda.tngp.broker.transport.worker.WorkerServiceNames.*;
import static org.camunda.tngp.broker.wf.runtime.WfRuntimeServiceNames.*;
import static org.camunda.tngp.broker.taskqueue.TaskQueueServiceNames.*;

import org.camunda.tngp.broker.services.DeferredResponsePoolService;
import org.camunda.tngp.broker.system.Component;
import org.camunda.tngp.broker.system.ConfigurationManager;
import org.camunda.tngp.broker.system.SystemContext;
import org.camunda.tngp.broker.transport.worker.AsyncRequestWorkerService;
import org.camunda.tngp.broker.transport.worker.BrokerRequestDispatcher;
import org.camunda.tngp.broker.transport.worker.BrokerRequestWorkerContextService;
import org.camunda.tngp.broker.transport.worker.CompositeRequestDispatcher;
import org.camunda.tngp.broker.transport.worker.spi.BrokerRequestHandler;
import org.camunda.tngp.broker.wf.cfg.WfComponentCfg;
import org.camunda.tngp.broker.wf.runtime.LogProcessingTask;
import org.camunda.tngp.broker.wf.runtime.WfRuntimeContext;
import org.camunda.tngp.broker.wf.runtime.WfRuntimeManagerService;
import org.camunda.tngp.broker.wf.runtime.request.handler.DeployBpmnResourceHandler;
import org.camunda.tngp.broker.wf.runtime.request.handler.StartWorkflowInstanceHandler;
import org.camunda.tngp.servicecontainer.ServiceContainer;
import org.camunda.tngp.servicecontainer.ServiceName;
import org.camunda.tngp.transport.requestresponse.server.AsyncRequestWorkerContext;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponsePool;
import org.camunda.tngp.transport.requestresponse.server.WorkerTask;

public class WfComponent implements Component
{
    public static final String WORKER_NAME = "wf-worker.0";

    @Override
    public void init(SystemContext context)
    {
        final ServiceContainer serviceContainer = context.getServiceContainer();
        final ConfigurationManager configurationManager = context.getConfigurationManager();
        final WfComponentCfg cfg = configurationManager.readEntry("workflow", WfComponentCfg.class);

        final WfRuntimeManagerService wfRuntimeManagerService = new WfRuntimeManagerService(configurationManager);
        serviceContainer.createService(WF_RUNTIME_MANAGER_NAME, wfRuntimeManagerService)
            .groupReference(WF_RUNTIME_CONTEXT_GROUP_NAME, wfRuntimeManagerService.getResourceContextsReference())
            .groupReference(TASK_QUEUE_CONTEXT_SERVICE_GROUP_NAME, wfRuntimeManagerService.getTaskQueueContextsGroupReference())
            .install();

        final int numberOfWorkers = cfg.numberOfWorkers;
        if (numberOfWorkers != 1)
        {
            throw new RuntimeException("Illegal value for config property 'workflow.numberOfWorkers': " + numberOfWorkers + " only 1 is supported.");
        }
        final int perWorkerResponsePoolCapacity = cfg.perWorkerResponsePoolCapacity;

        final BrokerRequestDispatcher<WfRuntimeContext> runtimeDispatcher = new BrokerRequestDispatcher<>(wfRuntimeManagerService, 2, new BrokerRequestHandler[]
        {
            new DeployBpmnResourceHandler(),
            new StartWorkflowInstanceHandler(),
        });

        final WfWorkerContext workerContext = new WfWorkerContext();
        workerContext.setWfRuntimeManager(wfRuntimeManagerService);

        workerContext.setRequestHandler(new CompositeRequestDispatcher<>(new BrokerRequestDispatcher[]
        {
            runtimeDispatcher
        }));

        // TODO: does this still need to be an array?
        workerContext.setWorkerTasks(new WorkerTask[]
        {
            new LogProcessingTask()
        });

        final DeferredResponsePoolService responsePoolService = new DeferredResponsePoolService(perWorkerResponsePoolCapacity);
        final AsyncRequestWorkerService workerService = new AsyncRequestWorkerService();
        final BrokerRequestWorkerContextService workerContextService = new BrokerRequestWorkerContextService(workerContext);

        final ServiceName<DeferredResponsePool> responsePoolServiceName = workerResponsePoolServiceName(WORKER_NAME);
        serviceContainer.createService(responsePoolServiceName, responsePoolService)
            .dependency(TRANSPORT_SEND_BUFFER, responsePoolService.getSendBufferInector())
            .install();

        final ServiceName<AsyncRequestWorkerContext> workerContextServiceName = workerContextServiceName(WORKER_NAME);
        serviceContainer.createService(workerContextServiceName, workerContextService)
            .dependency(responsePoolServiceName, workerContextService.getResponsePoolInjector())
            .dependency(serverSocketBindingReceiveBufferName(CLIENT_API_SOCKET_BINDING_NAME), workerContextService.getRequestBufferInjector())
            .install();

        serviceContainer.createService(workerServiceName(WORKER_NAME), workerService)
            .dependency(workerContextServiceName, workerService.getWorkerContextInjector())
            .dependency(AGENT_RUNNER_SERVICE, workerService.getAgentRunnerInjector())
            .dependency(WF_RUNTIME_MANAGER_NAME)
            .install();
    }


}
