package org.camunda.tngp.broker.wf;

import static org.camunda.tngp.broker.log.LogServiceNames.LOG_WRITE_BUFFER_SERVICE;
import static org.camunda.tngp.broker.system.SystemServiceNames.AGENT_RUNNER_SERVICE;
import static org.camunda.tngp.broker.transport.TransportServiceNames.CLIENT_API_SOCKET_BINDING_NAME;
import static org.camunda.tngp.broker.transport.TransportServiceNames.TRANSPORT_SEND_BUFFER;
import static org.camunda.tngp.broker.transport.TransportServiceNames.serverSocketBindingReceiveBufferName;
import static org.camunda.tngp.broker.transport.worker.WorkerServiceNames.workerContextServiceName;
import static org.camunda.tngp.broker.transport.worker.WorkerServiceNames.workerResponsePoolServiceName;
import static org.camunda.tngp.broker.transport.worker.WorkerServiceNames.workerServiceName;
import static org.camunda.tngp.broker.wf.repository.WfRepositoryServiceNames.WF_REPOSITORY_MANAGER_NAME;
import static org.camunda.tngp.broker.wf.runtime.WfRuntimeServiceNames.WF_RUNTIME_MANAGER_NAME;

import org.camunda.tngp.broker.services.DeferredResponsePoolService;
import org.camunda.tngp.broker.system.Component;
import org.camunda.tngp.broker.system.ConfigurationManager;
import org.camunda.tngp.broker.system.SystemContext;
import org.camunda.tngp.broker.taskqueue.TaskQueueContext;
import org.camunda.tngp.broker.taskqueue.TaskQueueContextService;
import org.camunda.tngp.broker.transport.worker.AsyncRequestWorkerService;
import org.camunda.tngp.broker.transport.worker.BrokerRequestDispatcher;
import org.camunda.tngp.broker.transport.worker.BrokerRequestWorkerContextService;
import org.camunda.tngp.broker.transport.worker.CompositeRequestDispatcher;
import org.camunda.tngp.broker.transport.worker.spi.BrokerRequestHandler;
import org.camunda.tngp.broker.wf.cfg.WfComponentCfg;
import org.camunda.tngp.broker.wf.repository.WfRepositoryContext;
import org.camunda.tngp.broker.wf.repository.WfRepositoryContextService;
import org.camunda.tngp.broker.wf.repository.WfRepositoryManagerService;
import org.camunda.tngp.broker.wf.repository.request.handler.DeployBpmnResourceHandler;
import org.camunda.tngp.broker.wf.runtime.LogProcessingTask;
import org.camunda.tngp.broker.wf.runtime.WfRuntimeContext;
import org.camunda.tngp.broker.wf.runtime.WfRuntimeManagerService;
import org.camunda.tngp.broker.wf.runtime.WfRuntimeServiceNames;
import org.camunda.tngp.broker.wf.runtime.request.handler.StartWorkflowInstanceHandler;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceContainer;
import org.camunda.tngp.servicecontainer.ServiceName;
import org.camunda.tngp.servicecontainer.ServiceTracker;
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

        final WfRepositoryManagerService wfRepositoryManagerService = new WfRepositoryManagerService(configurationManager);
        serviceContainer.createService(WF_REPOSITORY_MANAGER_NAME, wfRepositoryManagerService)
            .install();

        final WfRuntimeManagerService wfRuntimeManagerService = new WfRuntimeManagerService(configurationManager);
        serviceContainer.createService(WF_RUNTIME_MANAGER_NAME, wfRuntimeManagerService)
            .install();

        final int numberOfWorkers = cfg.numberOfWorkers;
        if (numberOfWorkers != 1)
        {
            throw new RuntimeException("Illegal value for config property 'workflow.numberOfWorkers': " + numberOfWorkers + " only 1 is supported.");
        }
        final int perWorkerResponsePoolCapacity = cfg.perWorkerResponsePoolCapacity;

        final BrokerRequestDispatcher<WfRuntimeContext> runtimeDispatcher = new BrokerRequestDispatcher<>(wfRuntimeManagerService, 3, new BrokerRequestHandler[]
        {
            new StartWorkflowInstanceHandler()
        });

        final BrokerRequestDispatcher<WfRepositoryContext> repositoryDispatcher = new BrokerRequestDispatcher<>(wfRepositoryManagerService, 2, new BrokerRequestHandler[]
        {
            new DeployBpmnResourceHandler()
        });

        final WfWorkerContext workerContext = new WfWorkerContext();
        workerContext.setWfRepositoryManager(wfRepositoryManagerService);
        workerContext.setWfRuntimeManager(wfRuntimeManagerService);

        workerContext.setRequestHandler(new CompositeRequestDispatcher<>(new BrokerRequestDispatcher[]
        {
            runtimeDispatcher,
            repositoryDispatcher
        }));

        // TODO: does this still need to be an array?
        workerContext.setWorkerTasks(new WorkerTask[]
        {
            new LogProcessingTask()
        });

        final DeferredResponsePoolService responsePoolService = new DeferredResponsePoolService(perWorkerResponsePoolCapacity);
        final AsyncRequestWorkerService workerService = new AsyncRequestWorkerService();
        final BrokerRequestWorkerContextService workerContextService = new BrokerRequestWorkerContextService(workerContext);

        final ServiceName<DeferredResponsePool> responsePoolServiceName = serviceContainer.createService(workerResponsePoolServiceName(WORKER_NAME), responsePoolService)
            .dependency(TRANSPORT_SEND_BUFFER, responsePoolService.getSendBufferInector())
            .install();

        final ServiceName<AsyncRequestWorkerContext> workerContextServiceName = serviceContainer.createService(workerContextServiceName(WORKER_NAME), workerContextService)
            .dependency(responsePoolServiceName, workerContextService.getResponsePoolInjector())
            .dependency(LOG_WRITE_BUFFER_SERVICE, workerContextService.getAsyncWorkBufferInjector())
            .dependency(serverSocketBindingReceiveBufferName(CLIENT_API_SOCKET_BINDING_NAME), workerContextService.getRequestBufferInjector())
            .install();

        serviceContainer.createService(workerServiceName(WORKER_NAME), workerService)
            .dependency(workerContextServiceName, workerService.getWorkerContextInjector())
            .dependency(AGENT_RUNNER_SERVICE, workerService.getAgentRunnerInjector())
            .dependency(WF_REPOSITORY_MANAGER_NAME)
            .dependency(WF_RUNTIME_MANAGER_NAME)
            .install();

        serviceContainer.registerTracker(new ServiceTracker()
        {
            @Override
            public <S> void onServiceStopping(ServiceName<S> name, Service<S> service)
            {
            }

            @Override
            public <S> void onServiceStarted(ServiceName<S> name, Service<S> service)
            {
                listenToTaskQueueLog(name, service);
                listenToWfRepositoryLog(name, service);
            }

            @Override
            public <S> void onTrackerRegistration(ServiceName<S> name, Service<S> service)
            {
                listenToTaskQueueLog(name, service);
                listenToWfRepositoryLog(name, service);
            }

            protected <S> void listenToTaskQueueLog(ServiceName<S> name, Service<S> service)
            {
                if (service instanceof TaskQueueContextService)
                {
                    final ServiceName<TaskQueueContext> taskQueueContextServiceName = (ServiceName<TaskQueueContext>) name;

                    final ServiceName<Log> taskQueueLogName = ((TaskQueueContextService) service).getLogInjector().getInjectedServiceName();

                    final TaskQueueLogProcessorService taskQueueLogReaderService = new TaskQueueLogProcessorService();

                    serviceContainer
                        .createService(WfRuntimeServiceNames.taskEventHandlerService(taskQueueLogName.toString()), taskQueueLogReaderService)
                        .dependency(taskQueueContextServiceName, taskQueueLogReaderService.getTaskQueueContext())
                        .dependency(WfRuntimeServiceNames.WF_RUNTIME_MANAGER_NAME, taskQueueLogReaderService.getWfRuntimeManager())
                        .install();
                }
            }

            protected <S> void listenToWfRepositoryLog(ServiceName<S> name, Service<S> service)
            {
                if (service instanceof WfRepositoryContextService)
                {
                    final ServiceName<WfRepositoryContext> wfRepositoryContextServiceName = (ServiceName<WfRepositoryContext>) name;

                    final ServiceName<Log> logName = ((WfRepositoryContextService) service).getWfDefinitionLogInjector().getInjectedServiceName();

                    final WfDefinitionLogProcessorService logReaderService = new WfDefinitionLogProcessorService();

                    serviceContainer
                        .createService(WfRuntimeServiceNames.taskEventHandlerService(logName.toString()), logReaderService)
                        .dependency(wfRepositoryContextServiceName, logReaderService.getWfRepositoryContextInjector())
                        .dependency(WfRuntimeServiceNames.WF_RUNTIME_MANAGER_NAME, logReaderService.getWfRuntimeManagerInjector())
                        .install();
                }
            }
        });
    }


}
