package org.camunda.tngp.broker.taskqueue;

import static org.camunda.tngp.broker.system.SystemServiceNames.*;
import static org.camunda.tngp.broker.transport.TransportServiceNames.*;
import static org.camunda.tngp.broker.taskqueue.TaskQueueServiceNames.*;
import static org.camunda.tngp.broker.transport.worker.WorkerServiceNames.*;
import static org.camunda.tngp.broker.wf.runtime.WfRuntimeServiceNames.*;

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
import org.camunda.tngp.protocol.taskqueue.ProvideSubscriptionCreditsDecoder;
import org.camunda.tngp.servicecontainer.ServiceContainer;
import org.camunda.tngp.servicecontainer.ServiceName;
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
        serviceContainer.createService(TASK_QUEUE_MANAGER, taskQueueManagerService)
            .groupReference(TASK_QUEUE_CONTEXT_SERVICE_GROUP_NAME, taskQueueManagerService.getResourceContextsReference())
            .groupReference(WF_RUNTIME_CONTEXT_GROUP_NAME, taskQueueManagerService.getWfRuntimeContextsReference())
            .install();

        startWorkers(serviceContainer, cfg, taskQueueManagerService, TASK_QUEUE_MANAGER);
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

        final ServiceName<DeferredResponsePool> responsePoolServiceName = workerResponsePoolServiceName(WORKER_NAME);
        serviceContainer.createService(responsePoolServiceName, responsePoolService)
            .dependency(TRANSPORT_SEND_BUFFER, responsePoolService.getSendBufferInector())
            .install();

        serviceContainer.createService(workerDataFramePoolServiceName(WORKER_NAME), dataFramePoolService)
            .dependency(TRANSPORT_SEND_BUFFER, dataFramePoolService.getSendBufferInector())
            .install();

        final ServiceName<AsyncRequestWorkerContext> workerContextServiceName = workerContextServiceName(WORKER_NAME);
        serviceContainer.createService(workerContextServiceName, brokerWorkerContextService)
            .dependency(responsePoolServiceName, brokerWorkerContextService.getResponsePoolInjector())
            .dependency(serverSocketBindingReceiveBufferName(CLIENT_API_SOCKET_BINDING_NAME), brokerWorkerContextService.getRequestBufferInjector())
            .dependency(TASK_QUEUE_MANAGER, brokerWorkerContextService.getTaskQueueManagerInjector())
            .install();

        serviceContainer.createService(workerServiceName(WORKER_NAME), workerService)
            .dependency(workerContextServiceName, workerService.getWorkerContextInjector())
            .dependency(AGENT_RUNNER_SERVICE, workerService.getAgentRunnerInjector())
            .dependency(taskQueueManagerServiceName)
            .install();

    }

}
