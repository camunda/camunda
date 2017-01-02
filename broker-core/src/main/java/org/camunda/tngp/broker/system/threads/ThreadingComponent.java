package org.camunda.tngp.broker.system.threads;

import static org.camunda.tngp.broker.system.SystemServiceNames.AGENT_RUNNER_SERVICE;
import static org.camunda.tngp.broker.system.SystemServiceNames.COUNTERS_MANAGER_SERVICE;

import org.camunda.tngp.broker.system.Component;
import org.camunda.tngp.broker.system.SystemContext;
import org.camunda.tngp.servicecontainer.ServiceContainer;

public class ThreadingComponent implements Component
{
    @Override
    public void init(SystemContext context)
    {
        final ServiceContainer serviceContainer = context.getServiceContainer();

        final AgentRunnerServicesImpl service = new AgentRunnerServicesImpl(context.getConfigurationManager());
        serviceContainer.createService(AGENT_RUNNER_SERVICE, service)
            .dependency(COUNTERS_MANAGER_SERVICE, service.getCountersManagerInjector())
            .install();
    }

}
