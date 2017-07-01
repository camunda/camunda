package io.zeebe.broker.system;

import io.zeebe.broker.services.Counters;
import io.zeebe.broker.system.executor.ScheduledExecutor;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.util.actor.ActorScheduler;

public class SystemServiceNames
{
    public static final ServiceName<ActorScheduler> ACTOR_SCHEDULER_SERVICE = ServiceName.newServiceName("broker.task.scheduler", ActorScheduler.class);

    public static final ServiceName<Counters> COUNTERS_MANAGER_SERVICE = ServiceName.newServiceName("broker.countersManager", Counters.class);

    public static final ServiceName<ScheduledExecutor> EXECUTOR_SERVICE = ServiceName.newServiceName("broker.executor", ScheduledExecutor.class);
}
