package org.camunda.tngp.broker.logstreams;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

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
            String snapshotDirectory = config.snapshotDirectory;
            if (config.useTempSnapshotDirectory)
            {
                try
                {
                    final File tempDir = Files.createTempDirectory("tngp-snapshot-").toFile();
                    System.out.format("Created temp directory for snapshots at location %s.\n", tempDir);
                    snapshotDirectory = tempDir.getAbsolutePath();
                }
                catch (IOException e)
                {
                    throw new RuntimeException("Could not create temp directory for snapshots ", e);
                }
            }
            else if (snapshotDirectory == null || snapshotDirectory.isEmpty())
            {
                throw new RuntimeException(String.format("Cannot create snapshot storage, no snapshot directory provided."));
            }

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
