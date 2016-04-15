package org.camunda.tngp.broker.log;

import static org.camunda.tngp.broker.log.LogServiceNames.LOG_MANAGER_SERVICE;

import org.camunda.tngp.broker.system.Component;
import org.camunda.tngp.broker.system.SystemContext;

public class LogComponent implements Component
{

    @Override
    public void init(SystemContext context)
    {
        final LogManagerService logManager = new LogManagerService(context.getConfigurationManager());

        context.getServiceContainer().installService(LOG_MANAGER_SERVICE, logManager)
            .done();
    }

}
