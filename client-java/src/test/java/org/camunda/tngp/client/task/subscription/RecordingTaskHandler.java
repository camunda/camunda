package org.camunda.tngp.client.task.subscription;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.camunda.tngp.client.task.Task;
import org.camunda.tngp.client.task.TaskHandler;

public class RecordingTaskHandler implements TaskHandler
{
    protected List<Task> handledTasks = Collections.synchronizedList(new ArrayList<>());
    protected int nextTaskHandler = 0;
    protected final TaskHandler[] taskHandlers;

    public RecordingTaskHandler()
    {
        this(task ->
        {
            // do nothing
        });
    }

    public RecordingTaskHandler(TaskHandler... taskHandlers)
    {
        this.taskHandlers = taskHandlers;
    }

    @Override
    public void handle(Task task)
    {
        final TaskHandler handler = taskHandlers[nextTaskHandler];
        nextTaskHandler = Math.min(nextTaskHandler + 1, taskHandlers.length - 1);

        try
        {
            handler.handle(task);
        }
        finally
        {
            handledTasks.add(task);
        }
    }

    public List<Task> getHandledTasks()
    {
        return handledTasks;
    }

    public void clear()
    {
        handledTasks.clear();
    }

}
