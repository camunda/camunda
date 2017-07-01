package io.zeebe.broker.logstreams;


import io.zeebe.broker.logstreams.cfg.SnapshotStorageCfg;
import io.zeebe.broker.system.ConfigurationManager;
import io.zeebe.logstreams.LogStreams;
import io.zeebe.logstreams.spi.SnapshotStorage;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;

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
            final String snapshotDirectory = config.directory;

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
