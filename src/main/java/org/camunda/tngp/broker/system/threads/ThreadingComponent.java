package org.camunda.tngp.broker.system.threads;

import static org.camunda.tngp.broker.system.SystemServiceNames.*;

import org.camunda.tngp.broker.system.Component;
import org.camunda.tngp.broker.system.SystemContext;

public class ThreadingComponent implements Component
{
    @Override
    public void init(SystemContext context)
    {
        final AgentRunnterServiceImpl service = new AgentRunnterServiceImpl(context.getConfigurationManager());

        context.getServiceContainer()
            .installService(AGENT_RUNNER_SERVICE, service)
            .done();
    }

}
