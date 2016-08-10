package org.camunda.tngp.broker.system.metrics;

import static org.camunda.tngp.broker.system.SystemServiceNames.COUNTERS_MANAGER_SERVICE;

import org.camunda.tngp.broker.services.CountersManagerService;
import org.camunda.tngp.broker.system.Component;
import org.camunda.tngp.broker.system.SystemContext;
import org.camunda.tngp.servicecontainer.ServiceContainer;

public class MetricsComponent implements Component
{

    @Override
    public void init(SystemContext context)
    {
        final ServiceContainer serviceContainer = context.getServiceContainer();

        final CountersManagerService service = new CountersManagerService(context.getConfigurationManager());
        serviceContainer.createService(COUNTERS_MANAGER_SERVICE, service)
            .install();
    }

}
