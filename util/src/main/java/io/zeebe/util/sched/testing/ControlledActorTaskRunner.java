package io.zeebe.util.sched.testing;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

import io.zeebe.util.LangUtil;
import io.zeebe.util.sched.ActorTaskRunner;
import io.zeebe.util.sched.ZbActorScheduler;
import io.zeebe.util.sched.metrics.ActorRunnerMetrics;

public class ControlledActorTaskRunner extends ActorTaskRunner
{
    private CyclicBarrier barrier = new CyclicBarrier(2);

    public ControlledActorTaskRunner(ZbActorScheduler scheduler, int runnerId, ActorRunnerMetrics metrics)
    {
        super(scheduler, runnerId, metrics);
        idleStrategy = new ControlledIdleStartegy();
    }

    class ControlledIdleStartegy extends ActorTaskRunnerIdleStrategy
    {
        @Override
        protected void idle()
        {
            super.idle();

            try
            {
                barrier.await();
            }
            catch (InterruptedException | BrokenBarrierException e)
            {
                LangUtil.rethrowUnchecked(e);
            }
        }
    }

    public void workUntilDone()
    {
        try
        {
            barrier.await(); // work at least 1 full cycle until the runner becomes idle after having been idle
            barrier.await();
        }
        catch (InterruptedException | BrokenBarrierException e)
        {
            LangUtil.rethrowUnchecked(e);
        }
    }

}
