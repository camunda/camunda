package org.camunda.tngp.broker.taskqueue;

import static org.camunda.tngp.broker.log.LogServiceNames.LOG_WRITE_BUFFER_SERVICE;
import static org.camunda.tngp.broker.system.SystemServiceNames.AGENT_RUNNER_SERVICE;
import static org.camunda.tngp.broker.taskqueue.TaskQueueServiceNames.TASK_QUEUE_MANAGER;
import static org.camunda.tngp.broker.transport.TransportServiceNames.CLIENT_API_SOCKET_BINDING_NAME;
import static org.camunda.tngp.broker.transport.TransportServiceNames.TRANSPORT_SEND_BUFFER;
import static org.camunda.tngp.broker.transport.TransportServiceNames.serverSocketBindingReceiveBufferName;
import static org.camunda.tngp.broker.transport.worker.WorkerServiceNames.workerContextServiceName;
import static org.camunda.tngp.broker.transport.worker.WorkerServiceNames.workerResponsePoolServiceName;
import static org.camunda.tngp.broker.transport.worker.WorkerServiceNames.workerServiceName;

import org.camunda.tngp.broker.services.DeferredResponsePoolService;
import org.camunda.tngp.broker.system.Component;
import org.camunda.tngp.broker.system.ConfigurationManager;
import org.camunda.tngp.broker.system.SystemContext;
import org.camunda.tngp.broker.taskqueue.cfg.TaskQueueComponentCfg;
import org.camunda.tngp.broker.taskqueue.handler.CompleteTaskHandler;
import org.camunda.tngp.broker.taskqueue.handler.CreateTaskInstanceHandler;
import org.camunda.tngp.broker.taskqueue.handler.LockTaskBatchHandler;
import org.camunda.tngp.broker.transport.worker.AsyncRequestWorkerService;
import org.camunda.tngp.broker.transport.worker.BrokerRequestDispatcher;
import org.camunda.tngp.broker.transport.worker.spi.BrokerRequestHandler;
import org.camunda.tngp.broker.wf.runtime.WfRuntimeContextService;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceContainer;
import org.camunda.tngp.servicecontainer.ServiceName;
import org.camunda.tngp.servicecontainer.ServiceTracker;
import org.camunda.tngp.transport.requestresponse.server.AsyncRequestWorkerContext;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponsePool;
import org.camunda.tngp.transport.requestresponse.server.WorkerTask;

public class TaskQueueComponent implements Component
{

    @Override
    public void init(SystemContext context)
    {
        final ServiceContainer serviceContainer = context.getServiceContainer();
        final ConfigurationManager configurationManager = context.getConfigurationManager();
        final TaskQueueComponentCfg cfg = configurationManager.readEntry("task-queues", TaskQueueComponentCfg.class);

        final TaskQueueManagerService taskQueueManagerService = new TaskQueueManagerService(configurationManager);
        final ServiceName<TaskQueueManager> taskQueueManagerServiceName = serviceContainer.createService(TASK_QUEUE_MANAGER, taskQueueManagerService)
            .install();

        startWorkers(serviceContainer, cfg, taskQueueManagerService, taskQueueManagerServiceName);
    }

    protected void startWorkers(final ServiceContainer serviceContainer, final TaskQueueComponentCfg cfg,
            final TaskQueueManagerService taskQueueManagerService,
            final ServiceName<TaskQueueManager> taskQueueManagerServiceName)
    {
        final int numberOfWorkers = cfg.numberOfWorkers;
        if (numberOfWorkers != 1)
        {
            throw new RuntimeException("Illegal value for config property 'task-queues.numberOfWorkers': " +
                    numberOfWorkers + " only 1 is supported.");
        }
        final int perWorkerResponsePoolCapacity = cfg.perWorkerResponsePoolCapacity;

        final BrokerRequestDispatcher<TaskQueueContext> taskQueueRequestDispatcher = new BrokerRequestDispatcher<>(taskQueueManagerService, 1, new BrokerRequestHandler[] {
            new CreateTaskInstanceHandler(),
            new LockTaskBatchHandler(),
            new CompleteTaskHandler()
        });

        final TaskQueueWorkerContext workerContext = new TaskQueueWorkerContext();
        workerContext.setRequestHandler(taskQueueRequestDispatcher);
        workerContext.setWorkerTasks(new WorkerTask[]
        {
            new InputLogProcessingTask(),
            new TaskQueueIndexWriteWorkerTask()
        });

        final DeferredResponsePoolService responsePoolService = new DeferredResponsePoolService(perWorkerResponsePoolCapacity);
        final AsyncRequestWorkerService workerService = new AsyncRequestWorkerService();
        final TaskQueueWorkerContextService brokerWorkerContextService = new TaskQueueWorkerContextService(workerContext);

        final String workerName = "task-queue-worker.0";

        final ServiceName<DeferredResponsePool> responsePoolServiceName = serviceContainer.createService(workerResponsePoolServiceName(workerName), responsePoolService)
            .dependency(TRANSPORT_SEND_BUFFER, responsePoolService.getSendBufferInector())
            .install();

        final ServiceName<AsyncRequestWorkerContext> workerContextServiceName = serviceContainer.createService(workerContextServiceName(workerName), brokerWorkerContextService)
            .dependency(responsePoolServiceName, brokerWorkerContextService.getResponsePoolInjector())
            .dependency(LOG_WRITE_BUFFER_SERVICE, brokerWorkerContextService.getAsyncWorkBufferInjector())
            .dependency(serverSocketBindingReceiveBufferName(CLIENT_API_SOCKET_BINDING_NAME), brokerWorkerContextService.getRequestBufferInjector())
            .dependency(TASK_QUEUE_MANAGER, brokerWorkerContextService.getTaskQueueManagerInjector())
            .install();

        serviceContainer.createService(workerServiceName(workerName), workerService)
            .dependency(workerContextServiceName, workerService.getWorkerContextInjector())
            .dependency(AGENT_RUNNER_SERVICE, workerService.getAgentRunnerInjector())
            .dependency(taskQueueManagerServiceName)
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
                listenToWorkflowRuntimeLog(service);
            }

            @Override
            public <S> void onTrackerRegistration(ServiceName<S> name, Service<S> service)
            {
                listenToWorkflowRuntimeLog(service);
            }

            protected void listenToWorkflowRuntimeLog(Service<?> service)
            {
                if (service instanceof WfRuntimeContextService)
                {
                    final ServiceName<Log> logName = ((WfRuntimeContextService) service).getLogInjector().getInjectedServiceName();

                    final WfInstanceLogProcessorService wfInstanceLogReaderService = new WfInstanceLogProcessorService();

                    serviceContainer
                        .createService(TaskQueueServiceNames.workflowEventHandlerService(logName.toString()), wfInstanceLogReaderService)
                        .dependency(logName, wfInstanceLogReaderService.getLogInjector())
                        .dependency(TASK_QUEUE_MANAGER, wfInstanceLogReaderService.getTaskQueueManager())
                        .install();
                }
            }
        });
    }

}
