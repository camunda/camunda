package org.camunda.tngp.broker.logstreams;

import static org.camunda.tngp.broker.logstreams.LogStreamServiceNames.*;

import org.camunda.tngp.broker.system.Component;
import org.camunda.tngp.broker.system.SystemContext;

public class LogStreamsComponent implements Component
{

    @Override
    public void init(SystemContext context)
    {
        final LogStreamsManagerService streamsManager = new LogStreamsManagerService(context.getConfigurationManager());

        context.getServiceContainer().createService(LOG_STREAMS_MANAGER_SERVICE, streamsManager)
            .install();
    }

}
