package io.zeebe.servicecontainer.impl;

import java.util.LinkedList;
import java.util.List;

import org.junit.Assert;

public class ControlledServiceContainer extends ServiceContainerImpl
{
    protected List<Runnable> shortRunning = new LinkedList<>();

    @Override
    public void start()
    {
        state = ContainerState.OPEN;
    }

    public void doWorkUntilDone()
    {
        final int maxIterations = 10000;
        int iterations = 0;

        while (doWork() > 0)
        {
            if (iterations > maxIterations)
            {
                Assert.fail("max iterations exhausted.");
            }

            ++iterations;
        }
    }

    @Override
    public void executeShortRunning(Runnable runnable)
    {
        shortRunning.add(runnable);
    }

    public void executeAsyncActions()
    {
        while (!shortRunning.isEmpty())
        {
            shortRunning.remove(0).run();
        }
    }
}
