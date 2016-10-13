package org.camunda.tngp.broker.log;

import org.camunda.tngp.broker.system.threads.AgentRunnerService;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.log.impl.agent.LogAgentContext;
import org.camunda.tngp.log.impl.agent.LogAppender;
import org.camunda.tngp.log.impl.agent.LogConductor;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceContext;

public class LogAgentContextService implements Service<LogAgentContext>
{
    protected final Injector<AgentRunnerService> agentRunnerServiceInjector = new Injector<>();
    protected final Injector<Dispatcher> logWriteBufferInjector = new Injector<>();

    protected final LogAgentContext agentContext;

    protected LogConductor logConductor;
    protected LogAppender logAppender;

    public LogAgentContextService()
    {
        agentContext = new LogAgentContext();
        agentContext.setWriteBufferExternallyManaged(true);
    }


    @Override
    public void start(ServiceContext serviceContext)
    {
        final AgentRunnerService agentRunnerService = agentRunnerServiceInjector.getValue();
        final Dispatcher logWriteBuffer = logWriteBufferInjector.getValue();
        agentContext.setWriteBuffer(logWriteBuffer);

        logConductor = new LogConductor(agentContext);
        logAppender = new LogAppender(agentContext);

        agentRunnerService.runConductorAgent(logConductor);
        agentRunnerService.runLogAgent(logAppender);
    }

    @Override
    public void stop()
    {
        final AgentRunnerService agentRunnerService = agentRunnerServiceInjector.getValue();

        try
        {
            logConductor.close().join();
        }
        finally
        {
            agentRunnerService.removeConductorAgent(logConductor);
            agentRunnerService.removeLogAgent(logAppender);
        }
    }

    @Override
    public LogAgentContext get()
    {
        return agentContext;
    }

    public Injector<Dispatcher> getLogWriteBufferInjector()
    {
        return logWriteBufferInjector;
    }

    public Injector<AgentRunnerService> getAgentRunnerServiceInjector()
    {
        return agentRunnerServiceInjector;
    }

}
