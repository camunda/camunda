package org.camunda.tngp.broker.logstreams;

import static org.camunda.tngp.broker.logstreams.LogStreamServiceNames.LOG_STREAMS_MANAGER_SERVICE;
import static org.camunda.tngp.broker.logstreams.LogStreamServiceNames.SNAPSHOT_STORAGE_SERVICE;
import static org.camunda.tngp.broker.system.SystemServiceNames.AGENT_RUNNER_SERVICE;

import org.camunda.tngp.broker.system.Component;
import org.camunda.tngp.broker.system.SystemContext;

public class LogStreamsComponent implements Component
{

    @Override
    public void init(SystemContext context)
    {
        final LogStreamsManagerService streamsManager = new LogStreamsManagerService(context.getConfigurationManager());
        context.getServiceContainer().createService(LOG_STREAMS_MANAGER_SERVICE, streamsManager)
            .dependency(AGENT_RUNNER_SERVICE, streamsManager.getAgentRunnerInjector())
            .install();

        final SnapshotStorageService snapshotStorageService = new SnapshotStorageService(context.getConfigurationManager());
        context.getServiceContainer().createService(SNAPSHOT_STORAGE_SERVICE, snapshotStorageService)
            .install();
    }

}
