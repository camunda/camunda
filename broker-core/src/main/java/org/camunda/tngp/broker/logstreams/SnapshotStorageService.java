package org.camunda.tngp.broker.logstreams;


import org.camunda.tngp.broker.logstreams.cfg.SnapshotStorageCfg;
import org.camunda.tngp.broker.system.ConfigurationManager;
import org.camunda.tngp.logstreams.LogStreams;
import org.camunda.tngp.logstreams.spi.SnapshotStorage;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.servicecontainer.ServiceStopContext;

public class SnapshotStorageService implements Service<SnapshotStorage>
{
    protected SnapshotStorageCfg config;
    private SnapshotStorage snapshotStorage;

    public SnapshotStorageService(ConfigurationManager configurationManager)
    {
        config = configurationManager.readEntry("snapshot", SnapshotStorageCfg.class);
    }

    @Override
    public void start(ServiceStartContext serviceContext)
    {
        serviceContext.run(() ->
        {
            final String snapshotDirectory = config.snapshotDirectory;

            snapshotStorage = LogStreams.createFsSnapshotStore(snapshotDirectory)
                .build();
        });
    }

    @Override
    public void stop(ServiceStopContext stopContext)
    {
        // nothing to do
    }

    @Override
    public SnapshotStorage get()
    {
        return snapshotStorage;
    }

}
