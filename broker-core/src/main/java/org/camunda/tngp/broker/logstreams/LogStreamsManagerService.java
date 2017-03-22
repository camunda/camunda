package org.camunda.tngp.broker.logstreams;

import java.util.List;

import org.camunda.tngp.broker.logstreams.cfg.LogStreamCfg;
import org.camunda.tngp.broker.logstreams.cfg.LogStreamsComponentCfg;
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
    protected LogStreamsComponentCfg logComponentConfig;
    protected List<LogStreamCfg> logCfgs;

    protected LogStreamsManager service;

    public LogStreamsManagerService(ConfigurationManager configurationManager)
    {
        logComponentConfig = configurationManager.readEntry("logs", LogStreamsComponentCfg.class);
        logCfgs = configurationManager.readList("log", LogStreamCfg.class);
    }

    @Override
    public void start(ServiceStartContext serviceContext)
    {
        this.serviceContext = serviceContext;

        serviceContext.run(() ->
        {
            service = new LogStreamsManager(logComponentConfig, agentRunnerInjector.getValue());
            for (LogStreamCfg logCfg : logCfgs)
            {
                service.createLogStream(logCfg);
            }
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
