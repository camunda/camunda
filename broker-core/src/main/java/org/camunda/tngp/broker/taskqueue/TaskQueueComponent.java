package org.camunda.tngp.broker.taskqueue;

import static org.camunda.tngp.broker.system.SystemServiceNames.AGENT_RUNNER_SERVICE;
import static org.camunda.tngp.broker.taskqueue.TaskQueueServiceNames.TASK_QUEUE_MANAGER;
import static org.camunda.tngp.broker.transport.TransportServiceNames.CLIENT_API_SOCKET_BINDING_NAME;
import static org.camunda.tngp.broker.transport.TransportServiceNames.TRANSPORT_SEND_BUFFER;
import static org.camunda.tngp.broker.transport.TransportServiceNames.serverSocketBindingReceiveBufferName;
import static org.camunda.tngp.broker.transport.worker.WorkerServiceNames.workerContextServiceName;
import static org.camunda.tngp.broker.transport.worker.WorkerServiceNames.workerDataFramePoolServiceName;
import static org.camunda.tngp.broker.transport.worker.WorkerServiceNames.workerResponsePoolServiceName;
import static org.camunda.tngp.broker.transport.worker.WorkerServiceNames.workerServiceName;

import org.camunda.tngp.broker.services.DataFramePoolService;
import org.camunda.tngp.broker.services.DeferredResponsePoolService;
import org.camunda.tngp.broker.system.Component;
import org.camunda.tngp.broker.system.ConfigurationManager;
import org.camunda.tngp.broker.system.SystemContext;
import org.camunda.tngp.broker.taskqueue.cfg.TaskQueueComponentCfg;
import org.camunda.tngp.broker.taskqueue.request.handler.CloseTaskSubscriptionHandler;
import org.camunda.tngp.broker.taskqueue.request.handler.CompleteTaskHandler;
import org.camunda.tngp.broker.taskqueue.request.handler.CreateTaskInstanceHandler;
import org.camunda.tngp.broker.taskqueue.request.handler.CreateTaskSubscriptionHandler;
import org.camunda.tngp.broker.taskqueue.request.handler.LockTaskBatchHandler;
import org.camunda.tngp.broker.taskqueue.request.handler.ProvideSubscriptionCreditsHandler;
import org.camunda.tngp.broker.taskqueue.subscription.TaskSubscriptionTask;
import org.camunda.tngp.broker.transport.worker.AsyncRequestWorkerService;
import org.camunda.tngp.broker.transport.worker.BrokerRequestDispatcher;
import org.camunda.tngp.broker.transport.worker.spi.BrokerRequestHandler;
import org.camunda.tngp.broker.wf.runtime.WfRuntimeContext;
import org.camunda.tngp.broker.wf.runtime.WfRuntimeContextService;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.protocol.taskqueue.ProvideSubscriptionCreditsDecoder;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceContainer;
import org.camunda.tngp.servicecontainer.ServiceName;
import org.camunda.tngp.servicecontainer.ServiceTracker;
import org.camunda.tngp.transport.requestresponse.server.AsyncRequestWorkerContext;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponsePool;
import org.camunda.tngp.transport.requestresponse.server.WorkerTask;

public class TaskQueueComponent implements Component
{
    public static final String WORKER_NAME = "task-queue-worker.0";

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
            new CompleteTaskHandler(),
            new CreateTaskSubscriptionHandler(),
            new CloseTaskSubscriptionHandler()
        });

        taskQueueRequestDispatcher.addDataFrameHandler(ProvideSubscriptionCreditsDecoder.TEMPLATE_ID, new ProvideSubscriptionCreditsHandler());

        final TaskQueueWorkerContext workerContext = new TaskQueueWorkerContext();
        workerContext.setRequestHandler(taskQueueRequestDispatcher);
        workerContext.setWorkerTasks(new WorkerTask[]
        {
            new LogProcessingTask(),
            new TaskSubscriptionTask()
        });

        final DeferredResponsePoolService responsePoolService = new DeferredResponsePoolService(perWorkerResponsePoolCapacity);
        final DataFramePoolService dataFramePoolService = new DataFramePoolService(perWorkerResponsePoolCapacity);
        final AsyncRequestWorkerService workerService = new AsyncRequestWorkerService();
        final TaskQueueWorkerContextService brokerWorkerContextService = new TaskQueueWorkerContextService(workerContext);

        final ServiceName<DeferredResponsePool> responsePoolServiceName = serviceContainer.createService(workerResponsePoolServiceName(WORKER_NAME), responsePoolService)
            .dependency(TRANSPORT_SEND_BUFFER, responsePoolService.getSendBufferInector())
            .install();

        serviceContainer.createService(workerDataFramePoolServiceName(WORKER_NAME), dataFramePoolService)
            .dependency(TRANSPORT_SEND_BUFFER, dataFramePoolService.getSendBufferInector())
            .install();

        final ServiceName<AsyncRequestWorkerContext> workerContextServiceName = serviceContainer.createService(workerContextServiceName(WORKER_NAME), brokerWorkerContextService)
            .dependency(responsePoolServiceName, brokerWorkerContextService.getResponsePoolInjector())
            .dependency(serverSocketBindingReceiveBufferName(CLIENT_API_SOCKET_BINDING_NAME), brokerWorkerContextService.getRequestBufferInjector())
            .dependency(TASK_QUEUE_MANAGER, brokerWorkerContextService.getTaskQueueManagerInjector())
            .install();

        serviceContainer.createService(workerServiceName(WORKER_NAME), workerService)
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
                listenToWorkflowRuntimeLog(name, service);
            }

            @Override
            public <S> void onTrackerRegistration(ServiceName<S> name, Service<S> service)
            {
                listenToWorkflowRuntimeLog(name, service);
            }

            protected <S> void listenToWorkflowRuntimeLog(ServiceName<S> name, Service<S> service)
            {
                if (service instanceof WfRuntimeContextService)
                {
                    final ServiceName<WfRuntimeContext> wfRuntimeContextServiceName = (ServiceName<WfRuntimeContext>) name;

                    final ServiceName<Log> wfRuntimeLogName = ((WfRuntimeContextService) service).getLogInjector().getInjectedServiceName();

                    final WfInstanceLogProcessorService wfInstanceLogReaderService = new WfInstanceLogProcessorService();

                    serviceContainer
                        .createService(TaskQueueServiceNames.workflowEventHandlerService(wfRuntimeLogName.toString()), wfInstanceLogReaderService)
                        .dependency(wfRuntimeContextServiceName, wfInstanceLogReaderService.getWfRuntimeContext())
                        .dependency(TASK_QUEUE_MANAGER, wfInstanceLogReaderService.getTaskQueueManager())
                        .install();
                }
            }
        });
    }

}
