package io.zeebe.util.sched;

import java.time.Duration;
import java.util.concurrent.*;

import io.zeebe.util.sched.ZbActorScheduler.ThreadAssignmentStrategy;
import io.zeebe.util.sched.metrics.TaskMetrics;
import org.agrona.concurrent.status.ConcurrentCountersManager;

/**
 * Used to submit {@link ActorTask ActorTasks} and Blocking Actions to
 * the scheduler's internal runners and queues.
 */
@SuppressWarnings("unchecked")
public class ActorExecutor
{
    private final ActorThread[] runners;
    private final ThreadPoolExecutor blockingTasksRunner;
    private final ConcurrentCountersManager countersManager;
    private final ThreadAssignmentStrategy runnerAssignmentStrategy;
    private final Duration blockingTasksShutdownTime;

    public ActorExecutor(ActorThread[] runners,
        ThreadPoolExecutor blockingTasksRunner,
        ConcurrentCountersManager countersManager,
        ThreadAssignmentStrategy runnerAssignmentStrategy,
        Duration blockingTasksShutdownTime)
    {
        this.runners = runners;
        this.blockingTasksRunner = blockingTasksRunner;
        this.countersManager = countersManager;
        this.runnerAssignmentStrategy = runnerAssignmentStrategy;
        this.blockingTasksShutdownTime = blockingTasksShutdownTime;
    }

    /**
     * Initially submit an actor to be managed by this schedueler. If the task
     * has already been submitted, use {@link #reSubmit(ActorTask)} instead.
     *
     * @param task
     *            the task to submit
     * @param collectTaskMetrics
     *            Controls whether metrics should be collected. (See
     *            {@link ZbActorScheduler#submitActor(ZbActor, boolean)})
     */
    public void submit(ActorTask task, boolean collectTaskMetrics)
    {
        final ActorThread assignedRunner = runnerAssignmentStrategy.nextRunner(runners);

        TaskMetrics taskMetrics = null;
        if (collectTaskMetrics)
        {
            taskMetrics = new TaskMetrics(task.getName(), countersManager);
        }
        task.onTaskScheduled(this, taskMetrics);

        assignedRunner.submit(task);
    }

    /**
     * External re-submit of a task which was already executed by this
     * scheduler: This is used when a task was in state
     * {@link ActorState#WAITING} and has either completed a blocking action or
     * was woken up by a non-actor thread.
     *
     * @param task
     *            the task to resubmit
     */
    public void reSubmit(ActorTask task)
    {
        final ActorThread assignedRunner = runnerAssignmentStrategy.nextRunner(runners);
        assignedRunner.submit(task);
    }

    /**
     * Sumbit a blocking action to run using the scheduler's blocking thread
     * pool
     *
     * @param action
     *            the action to submit
     */
    public void submitBlocking(Runnable action)
    {
        blockingTasksRunner.execute(action);
    }

    public void start()
    {
        for (int i = 0; i < runners.length; i++)
        {
            runners[i].start();
        }
    }

    public CompletableFuture<Void> closeAsync()
    {
        blockingTasksRunner.shutdown();

        final int runnerCount = runners.length;
        final CompletableFuture<Void>[] terminationFutures = new CompletableFuture[runnerCount];

        for (int i = 0; i < runnerCount; i++)
        {
            try
            {
                terminationFutures[i] = runners[i].close();
            }
            catch (IllegalStateException e)
            {
                e.printStackTrace();
                terminationFutures[i] = CompletableFuture.completedFuture(null);
            }
        }

        try
        {
            blockingTasksRunner.awaitTermination(blockingTasksShutdownTime.getSeconds(), TimeUnit.SECONDS);
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }

        return CompletableFuture.allOf(terminationFutures);
    }

    public ConcurrentCountersManager getCountersManager()
    {
        return countersManager;
    }
}
