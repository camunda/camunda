package org.camunda.tngp.broker.services;

import java.io.File;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.file.Files;

import org.camunda.tngp.broker.system.ConfigurationManager;
import org.camunda.tngp.broker.system.metrics.cfg.MetricsCfg;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceContext;

import uk.co.real_logic.agrona.BitUtil;
import uk.co.real_logic.agrona.IoUtil;
import uk.co.real_logic.agrona.LangUtil;
import uk.co.real_logic.agrona.concurrent.CountersManager;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class CountersManagerService implements Service<Counters>
{
    public final static int COUNTERS_FILE_SIZE = 1024 * 1024 * 4;
    public final static int LABELS_BUFFER_OFFSET = 0;
    public final static int LABELS_BUFFER_SIZE = (int) (COUNTERS_FILE_SIZE * 0.75);
    public final static int COUNTERS_BUFFER_OFFSET = BitUtil.align(LABELS_BUFFER_SIZE, 8);
    public final static int COUNTERS_BUFFER_SIZE = COUNTERS_FILE_SIZE - COUNTERS_BUFFER_OFFSET;

    protected final String countersFileName;

    protected CountersManager countersManager;

    protected MappedByteBuffer mappedCountersFile;

    protected Counters counters;

    public CountersManagerService(ConfigurationManager configurationManager)
    {
        final MetricsCfg metricsCfg = configurationManager.readEntry("metrics", MetricsCfg.class);

        if(metricsCfg.useTempCountersFile)
        {
            try
            {
                countersFileName = Files.createTempFile("tngp-counters", ".raw").toFile().getAbsolutePath();
            }
            catch (IOException e)
            {
                throw new RuntimeException("Could not create temp file for counters", e);
            }
        }
        else
        {
            countersFileName = metricsCfg.countersFileName;
        }

    }

    @Override
    public void start(ServiceContext serviceContext)
    {
        final File countersFile = new File(countersFileName);

        System.out.format("Using %s for counters.\n", countersFile.getAbsolutePath());

        IoUtil.deleteIfExists(countersFile);
        mappedCountersFile = IoUtil.mapNewFile(countersFile, COUNTERS_FILE_SIZE);

        final UnsafeBuffer labelsBuffer = new UnsafeBuffer(mappedCountersFile, LABELS_BUFFER_OFFSET, LABELS_BUFFER_SIZE);
        final UnsafeBuffer countersBuffer = new UnsafeBuffer(mappedCountersFile, COUNTERS_BUFFER_OFFSET, COUNTERS_BUFFER_SIZE);

        countersManager = new CountersManager(labelsBuffer, countersBuffer);

        counters = new Counters(countersManager, countersBuffer);
    }

    @Override
    public void stop()
    {
        countersManager.forEach((id,label) ->
        {
            System.err.format("Freeing counter %s \n", label);
            countersManager.free(id);
        });

        IoUtil.unmap(mappedCountersFile);
    }

    @Override
    public Counters get()
    {
        return counters;
    }

}
