package io.zeebe.broker.services;

import java.io.File;
import java.nio.MappedByteBuffer;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.system.ConfigurationManager;
import io.zeebe.broker.system.metrics.cfg.MetricsCfg;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import org.agrona.BitUtil;
import org.agrona.IoUtil;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.status.CountersManager;
import org.slf4j.Logger;

public class CountersManagerService implements Service<Counters>
{
    public static final Logger LOG = Loggers.SERVICES_LOGGER;

    public static final int COUNTERS_FILE_SIZE = 1024 * 1024 * 4;
    public static final int LABELS_BUFFER_OFFSET = 0;
    public static final int LABELS_BUFFER_SIZE = (int) (COUNTERS_FILE_SIZE * 0.75);
    public static final int COUNTERS_BUFFER_OFFSET = BitUtil.align(LABELS_BUFFER_SIZE, 8);
    public static final int COUNTERS_BUFFER_SIZE = COUNTERS_FILE_SIZE - COUNTERS_BUFFER_OFFSET;
    public static final String COUNTERS_FILE_NAME = "metrics.zeebe";

    protected final String countersFileName;
    protected CountersManager countersManager;
    protected MappedByteBuffer mappedCountersFile;
    protected Counters counters;

    public CountersManagerService(ConfigurationManager configurationManager)
    {
        final MetricsCfg metricsCfg = configurationManager.readEntry("metrics", MetricsCfg.class);
        countersFileName = metricsCfg.directory + COUNTERS_FILE_NAME;
    }

    @Override
    public void start(ServiceStartContext ctx)
    {
        ctx.run(() ->
        {
            final File countersFile = new File(countersFileName);
            countersFile.mkdirs();

            LOG.info("Using {} for counters", countersFile.getAbsolutePath());

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
                LOG.error("Freeing counter {}", label);
                countersManager.free(id);

            });

            // caution: if the file is used after unmap then a segmentation fault occurs
            IoUtil.unmap(mappedCountersFile);
        });
    }

    @Override
    public Counters get()
    {
        return counters;
    }

}
