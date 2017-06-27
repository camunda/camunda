package io.zeebe.broker.system;

import static io.zeebe.broker.system.SystemServiceNames.ACTOR_SCHEDULER_SERVICE;
import static io.zeebe.broker.system.SystemServiceNames.COUNTERS_MANAGER_SERVICE;
import static io.zeebe.broker.system.SystemServiceNames.EXECUTOR_SERVICE;

import io.zeebe.broker.services.CountersManagerService;
import io.zeebe.broker.system.executor.ScheduledExecutorService;
import io.zeebe.broker.system.threads.ActorSchedulerService;
import io.zeebe.servicecontainer.ServiceContainer;

public class SystemComponent implements Component
{

    @Override
    public void init(SystemContext context)
    {
        final ServiceContainer serviceContainer = context.getServiceContainer();

        final CountersManagerService countersManagerService = new CountersManagerService(context.getConfigurationManager());
        serviceContainer.createService(COUNTERS_MANAGER_SERVICE, countersManagerService)
            .install();

        final ActorSchedulerService agentRunnerService = new ActorSchedulerService(context.getConfigurationManager());
        serviceContainer.createService(ACTOR_SCHEDULER_SERVICE, agentRunnerService)
            .install();

        final ScheduledExecutorService executorService = new ScheduledExecutorService();
        serviceContainer.createService(EXECUTOR_SERVICE, executorService)
            .dependency(ACTOR_SCHEDULER_SERVICE, executorService.getActorSchedulerInjector())
            .install();
    }

}
