/**
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.client.task.subscription;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.zeebe.client.task.Task;
import io.zeebe.client.task.TaskHandler;

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
