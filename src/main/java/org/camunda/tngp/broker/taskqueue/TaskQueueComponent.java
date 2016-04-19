package org.camunda.tngp.broker.taskqueue;

import static org.camunda.tngp.broker.log.LogServiceNames.*;
import static org.camunda.tngp.broker.system.SystemServiceNames.*;
import static org.camunda.tngp.broker.taskqueue.TaskQueueServiceNames.*;
import static org.camunda.tngp.broker.transport.worker.WorkerServiceNames.*;
import static org.camunda.tngp.broker.transport.TransportServiceNames.*;

import org.camunda.tngp.broker.servicecontainer.ServiceContainer;
import org.camunda.tngp.broker.servicecontainer.ServiceName;
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
import org.camunda.tngp.broker.transport.worker.BrokerRequestWorkerContextService;
import org.camunda.tngp.broker.transport.worker.spi.BrokerRequestHandler;
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
        final ServiceName<TaskQueueManager> taskQueueManagerServiceName = serviceContainer.installService(TASK_QUEUE_MANAGER, taskQueueManagerService)
            .done();

        startWorkers(serviceContainer, cfg, taskQueueManagerService, taskQueueManagerServiceName);
    }

    protected void startWorkers(final ServiceContainer serviceContainer, final TaskQueueComponentCfg cfg,
            final TaskQueueManagerService taskQueueManagerService,
            final ServiceName<TaskQueueManager> taskQueueManagerServiceName)
    {
        final int numberOfWorkers = cfg.numberOfWorkers;
        if(numberOfWorkers != 1)
        {
            throw new RuntimeException("Illegal value for config property 'task-queues.numberOfWorkers': "+numberOfWorkers+" only 1 is supported.");
        }
        final int perWorkerResponsePoolCapacity = cfg.perWorkerResponsePoolCapacity;

        final BrokerRequestDispatcher<TaskQueueContext> taskQueueRequestDispatcher = new BrokerRequestDispatcher<>(taskQueueManagerService, 1, new BrokerRequestHandler[] {
                new CreateTaskInstanceHandler(),
                new LockTaskBatchHandler(),
                new CompleteTaskHandler()
        });

        final TaskQueueWorkerContext workerContext = new TaskQueueWorkerContext();
        workerContext.setRequestHandler(taskQueueRequestDispatcher);
        workerContext.setTaskQueueManager(taskQueueManagerService);
        workerContext.setWorkerTasks(new WorkerTask[]
        {
                new IndexWriteWorkerTask()
        });

        final DeferredResponsePoolService responsePoolService = new DeferredResponsePoolService(perWorkerResponsePoolCapacity);
        final AsyncRequestWorkerService workerService = new AsyncRequestWorkerService();
        final BrokerRequestWorkerContextService workerContextService = new BrokerRequestWorkerContextService(workerContext);

        final String workerName = "task-queue-worker.0";

        final ServiceName<DeferredResponsePool> responsePoolServiceName = serviceContainer.installService(workerResponsePoolServiceName(workerName), responsePoolService)
            .dependency(TRANSPORT_SEND_BUFFER, responsePoolService.getSendBufferInector())
            .done();

        final ServiceName<AsyncRequestWorkerContext> workerContextServiceName = serviceContainer.installService(workerContextServiceName(workerName), workerContextService)
            .dependency(responsePoolServiceName, workerContextService.getResponsePoolInjector())
            .dependency(LOG_WRITE_BUFFER_SERVICE, workerContextService.getAsyncWorkBufferInjector())
            .dependency(serverSocketBindingReceiveBufferName(CLIENT_API_SOCKET_BINDING_NAME), workerContextService.getRequestBufferInjector())
            .done();

        serviceContainer.installService(workerServiceName(workerName), workerService)
            .dependency(workerContextServiceName, workerService.getWorkerContextInjector())
            .dependency(AGENT_RUNNER_SERVICE, workerService.getAgentRunnerInjector())
            .dependency(taskQueueManagerServiceName)
            .done();
    }

}
