package org.camunda.tngp.broker.logstreams;

import static org.camunda.tngp.logstreams.log.LogStream.DEFAULT_PARTITION_ID;
import static org.camunda.tngp.logstreams.log.LogStream.DEFAULT_TOPIC_NAME_BUFFER;

import org.camunda.tngp.broker.logstreams.cfg.LogStreamsCfg;
import org.camunda.tngp.broker.system.ConfigurationManager;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.servicecontainer.ServiceStopContext;
import org.camunda.tngp.util.actor.ActorScheduler;

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
