package io.zeebe.util.sched.testing;

import java.time.Duration;

import io.zeebe.util.sched.ActorTaskRunner;
import io.zeebe.util.sched.ZbActorScheduler;
import io.zeebe.util.sched.metrics.ActorRunnerMetrics;
import org.agrona.concurrent.status.CountersManager;
import org.junit.Assert;

public class ControlledActorScheduler extends ZbActorScheduler
{
    private ControlledActorTaskRunner controlledActorTaskRunner;

    public ControlledActorScheduler(CountersManager countersManager)
    {
        super(1, countersManager);
        blockingTaskShutdownTime = Duration.ofSeconds(0);
    }

    @Override
    protected ActorTaskRunner createTaskRunner(int i, ActorRunnerMetrics metrics)
    {
        controlledActorTaskRunner = new ControlledActorTaskRunner(this, i, metrics);
        return controlledActorTaskRunner;
    }

    public void workUntilDone()
    {
        controlledActorTaskRunner.workUntilDone();
    }

    public void awaitBlockingTasksCompleted(int i)
    {
        final long currentTimeMillis = System.currentTimeMillis();

        while (System.currentTimeMillis() - currentTimeMillis < 5000)
        {
            final long completedTaskCount = blockingTasksRunner.getCompletedTaskCount();
            if (completedTaskCount >= i)
            {
                return;
            }
        }

        Assert.fail("could not complete " + i + " blocking tasks withing 5s");
    }

}
