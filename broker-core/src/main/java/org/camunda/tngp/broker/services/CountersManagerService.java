package org.camunda.tngp.broker.services;

import java.io.File;
import java.nio.MappedByteBuffer;

import org.agrona.BitUtil;
import org.agrona.IoUtil;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.status.CountersManager;
import org.camunda.tngp.broker.system.ConfigurationManager;
import org.camunda.tngp.broker.system.metrics.cfg.MetricsCfg;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.servicecontainer.ServiceStopContext;

public class CountersManagerService implements Service<Counters>
{
    public static final int COUNTERS_FILE_SIZE = 1024 * 1024 * 4;
    public static final int LABELS_BUFFER_OFFSET = 0;
    public static final int LABELS_BUFFER_SIZE = (int) (COUNTERS_FILE_SIZE * 0.75);
    public static final int COUNTERS_BUFFER_OFFSET = BitUtil.align(LABELS_BUFFER_SIZE, 8);
    public static final int COUNTERS_BUFFER_SIZE = COUNTERS_FILE_SIZE - COUNTERS_BUFFER_OFFSET;

    protected final String countersFileName;
    protected boolean deleteCountersFileOnExit;
    protected CountersManager countersManager;
    protected MappedByteBuffer mappedCountersFile;
    protected Counters counters;

    public CountersManagerService(ConfigurationManager configurationManager)
    {
        final MetricsCfg metricsCfg = configurationManager.readEntry("metrics", MetricsCfg.class);

        countersFileName = metricsCfg.countersFileName;


    }

    @Override
    public void start(ServiceStartContext ctx)
    {
        ctx.run(() ->
        {
            final File countersFile = new File(countersFileName);

            System.out.format("Using %s for counters.\n", countersFile.getAbsolutePath());

            IoUtil.deleteIfExists(countersFile);
            mappedCountersFile = IoUtil.mapNewFile(countersFile, COUNTERS_FILE_SIZE);

            final UnsafeBuffer labelsBuffer = new UnsafeBuffer(mappedCountersFile, LABELS_BUFFER_OFFSET, LABELS_BUFFER_SIZE);
            final UnsafeBuffer countersBuffer = new UnsafeBuffer(mappedCountersFile, COUNTERS_BUFFER_OFFSET, COUNTERS_BUFFER_SIZE);

            countersManager = new CountersManager(labelsBuffer, countersBuffer);

            counters = new Counters(countersManager, countersBuffer);
        });
    }

    @Override
    public void stop(ServiceStopContext ctx)
    {
        ctx.run(() ->
        {
            countersManager.forEach((id, label) ->
            {
                System.err.format("Freeing counter %s \n", label);
                countersManager.free(id);
            });

            IoUtil.unmap(mappedCountersFile);

            if (deleteCountersFileOnExit)
            {
                IoUtil.delete(new File(countersFileName), true);
            }
        });
    }

    @Override
    public Counters get()
    {
        return counters;
    }

}
