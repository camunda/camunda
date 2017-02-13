package org.camunda.tngp.client.task.impl;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

import org.agrona.concurrent.ManyToManyConcurrentArrayQueue;
import org.camunda.tngp.client.task.PollableTaskSubscription;
import org.camunda.tngp.client.task.TaskHandler;
import org.camunda.tngp.client.task.TaskSubscription;

public class TaskSubscriptionImpl implements TaskSubscription, PollableTaskSubscription
{

    protected final TaskHandler taskHandler;

    protected final ManyToManyConcurrentArrayQueue<TaskImpl> acquiredTasks;

    protected final String taskType;
    protected final int topicId;
    protected final long lockTime;
    protected final int lockOwner;
    protected final int capacity;

    protected final AtomicInteger state;

    protected final TaskAcquisition acquisition;

    protected long id;

    public static final int STATE_NEW = 0;
    public static final int STATE_OPENING = 1;
    public static final int STATE_OPEN = 2;
    public static final int STATE_CLOSING = 3;
    public static final int STATE_CLOSED = 4;
    public static final int STATE_TRANSITIONING = 99;

    protected IntConsumer stateObserver;

    protected boolean autoComplete;

    public TaskSubscriptionImpl(
            String taskType,
            int taskQueueId,
            long lockTime,
            int lockOwner,
            int lowerBoundCapacity,
            TaskAcquisition acquisition,
            boolean autoComplete)
    {
        this(null, taskType, taskQueueId, lockTime, lockOwner, lowerBoundCapacity, acquisition, autoComplete);
    }

    public TaskSubscriptionImpl(
            TaskHandler taskHandler,
            String taskType,
            int taskQueueId,
            long lockTime,
            int lockOwner,
            int lowerBoundCapacity,
            TaskAcquisition acquisition,
            boolean autoComplete)
    {
        this.taskHandler = taskHandler;
        this.taskType = taskType;
        this.topicId = taskQueueId;
        this.lockTime = lockTime;
        this.lockOwner = lockOwner;
        this.acquisition = acquisition;
        this.autoComplete = autoComplete;

        this.state = new AtomicInteger(STATE_NEW);
        this.acquiredTasks = new ManyToManyConcurrentArrayQueue<>(lowerBoundCapacity);
        this.capacity = acquiredTasks.capacity();
    }

    public void addTask(TaskImpl task)
    {
        final boolean added = acquiredTasks.offer(task);

        if (!added)
        {
            throw new RuntimeException("Cannot add any more tasks. Task queue saturated.");
        }
    }

    public String getTaskType()
    {
        return taskType;
    }

    public int getTopicId()
    {
        return topicId;
    }

    public long getLockTime()
    {
        return lockTime;
    }

    public int getLockOwner()
    {
        return lockOwner;
    }

    @Override
    public int poll(TaskHandler taskHandler)
    {
        final int currentlyAvailableTasks = size();
        int handledTasks = 0;

        TaskImpl task;

        // handledTasks < currentlyAvailableTasks avoids very long cycles that we spend in this method
        // in case the broker continuously produces new tasks
        while (handledTasks < currentlyAvailableTasks)
        {
            task = acquiredTasks.poll();
            if (task == null)
            {
                break;
            }

            handledTasks++;

            try
            {
                taskHandler.handle(task);

                if (autoComplete && !task.isCompleted())
                {
                    task.complete();
                }
            }
            catch (Exception e)
            {
                onTaskHandlingException(task, e);
            }

            submitCredits(1);
        }

        return handledTasks;
    }

    protected void onTaskHandlingException(TaskImpl task, Exception e)
    {
        // could become configurable in the future (e.g. unlock task or report an error via API)
        System.err.println("Exception during handling of task " + task.getId());
        e.printStackTrace(System.err);
    }

    public int poll()
    {
        return poll(taskHandler);
    }

    @Override
    public boolean isOpen()
    {
        return state.get() == STATE_OPEN;
    }

    public boolean isClosing()
    {
        return state.get() == STATE_CLOSING;
    }

    public boolean isClosed()
    {
        return state.get() == STATE_CLOSED;
    }

    public boolean isManagedSubscription()
    {
        return taskHandler != null;
    }

    public void doClose()
    {
        changeState(STATE_CLOSING, STATE_CLOSED, null);
    }

    public void doOpen()
    {
        changeState(STATE_OPENING, STATE_OPEN, null);
    }

    protected void changeState(int currentState, int newState, IntConsumer stateObserver)
    {
        state.compareAndSet(currentState, STATE_TRANSITIONING); // TODO: do something if not in source state

        if (stateObserver != null)
        {
            this.stateObserver = stateObserver;
        }
        final IntConsumer currentObserver = this.stateObserver;

        state.compareAndSet(STATE_TRANSITIONING, newState);

        if (currentObserver != null)
        {
            currentObserver.accept(newState);
        }
    }

    @Override
    public void close()
    {
        try
        {
            closeAsnyc().get();
        }
        catch (Exception e)
        {
            throw new RuntimeException("Could not close subscription", e);
        }
    }

    public Future<Void> closeAsnyc()
    {
        final CompletableFuture<Void> future = new CompletableFuture<>();
        changeState(STATE_OPEN, STATE_CLOSING, (s) ->
        {
            if (s == STATE_CLOSED)
            {
                future.complete(null);
            }
        });

        return future;
    }

    public void open()
    {
        try
        {
            openAsync().get();
        }
        catch (Exception e)
        {
            throw new RuntimeException("Could not open subscription", e);
        }
    }

    public Future<Void> openAsync()
    {
        final CompletableFuture<Void> future = new CompletableFuture<>();
        changeState(STATE_NEW, STATE_OPENING, (s) ->
        {
            if (s == STATE_OPEN)
            {
                future.complete(null);
            }
        });

        acquisition.scheduleCommand((a) -> a.openSubscription(this));

        return future;
    }

    public void submitCredits(int credits)
    {
        acquisition.scheduleCommand((a) -> a.updateCredits(this, credits));
    }

    public int size()
    {
        return acquiredTasks.size();
    }

    public boolean hasQueuedTasks()
    {
        return !acquiredTasks.isEmpty();
    }

    public int getState()
    {
        return state.get();
    }

    public int capacity()
    {
        return capacity;
    }

    public void setId(long id)
    {
        this.id = id;
    }

    public long getId()
    {
        return id;
    }

}
