package org.camunda.tngp.broker.logstreams;

import static org.camunda.tngp.broker.logstreams.cfg.LogStreamsCfg.DEFAULT_LOG_ID;
import static org.camunda.tngp.broker.logstreams.cfg.LogStreamsCfg.DEFAULT_LOG_NAME;

import org.camunda.tngp.broker.logstreams.cfg.LogStreamsCfg;
import org.camunda.tngp.broker.system.ConfigurationManager;
import org.camunda.tngp.broker.system.threads.AgentRunnerServices;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.servicecontainer.ServiceStopContext;

public class LogStreamsManagerService implements Service<LogStreamsManager>
{

    protected final Injector<AgentRunnerServices> agentRunnerInjector = new Injector<>();

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
            service = new LogStreamsManager(logStreamsCfg, agentRunnerInjector.getValue());

            service.createLogStream(DEFAULT_LOG_ID, DEFAULT_LOG_NAME);
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

    public Injector<AgentRunnerServices> getAgentRunnerInjector()
    {
        return agentRunnerInjector;
    }

}
