package io.zeebe.broker.logstreams;

import static io.zeebe.logstreams.log.LogStream.DEFAULT_PARTITION_ID;
import static io.zeebe.logstreams.log.LogStream.DEFAULT_TOPIC_NAME_BUFFER;

import io.zeebe.broker.logstreams.cfg.LogStreamsCfg;
import io.zeebe.broker.system.ConfigurationManager;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.util.actor.ActorScheduler;

public class LogStreamsManagerService implements Service<LogStreamsManager>
{

    protected final Injector<ActorScheduler> actorSchedulerInjector = new Injector<>();

    protected ServiceStartContext serviceContext;
    protected LogStreamsCfg logStreamsCfg;

    protected LogStreamsManager service;

    public LogStreamsManagerService(ConfigurationManager configurationManager)
    {
        logStreamsCfg = configurationManager.readEntry("logs", LogStreamsCfg.class);
    }

    @Override
    public void start(ServiceStartContext serviceContext)
    {
        this.serviceContext = serviceContext;

        serviceContext.run(() ->
        {
            service = new LogStreamsManager(logStreamsCfg, actorSchedulerInjector.getValue());

            service.createLogStream(DEFAULT_TOPIC_NAME_BUFFER, DEFAULT_PARTITION_ID);
        });
    }

    @Override
    public void stop(ServiceStopContext stopContext)
    {
        // nothing to do
    }

    @Override
    public LogStreamsManager get()
    {
        return service;
    }

    public Injector<ActorScheduler> getActorSchedulerInjector()
    {
        return actorSchedulerInjector;
    }

}
