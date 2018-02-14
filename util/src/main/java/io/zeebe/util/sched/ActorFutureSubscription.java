package io.zeebe.util.sched;

public class ActorFutureSubscription implements ActorSubscription
{

    private volatile boolean completed = false;
    private final ActorTask task;
    private final ActorJob callbackJob;

    public ActorFutureSubscription(ActorTask task, ActorJob callbackJob)
    {
        this.task = task;
        this.callbackJob = callbackJob;
    }

    @Override
    public boolean poll()
    {
        return completed;
    }

    @Override
    public ActorJob getJob()
    {
        return callbackJob;
    }

    @Override
    public boolean isRecurring()
    {
        return false;
    }

    public void trigger()
    {
        completed = true;

        if (task.tryWakeup())
        {
            final ActorTaskRunner taskRunner = ActorTaskRunner.current();
            if (taskRunner != null)
            {
                taskRunner.submit(task);
            }
            else
            {
                task.getScheduler().reSubmitActor(task);
            }
        }
    }

}
