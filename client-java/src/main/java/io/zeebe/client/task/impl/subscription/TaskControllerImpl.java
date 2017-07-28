/*
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
package io.zeebe.client.task.impl.subscription;

import io.zeebe.client.TasksClient;
import io.zeebe.client.event.impl.TaskEventImpl;
import io.zeebe.client.task.TaskController;

public class TaskControllerImpl implements TaskController
{

    protected final TasksClient tasksClient;
    protected final TaskEventImpl baseEvent;

    protected int state;
    protected static final int STATE_LOCKED = 0;
    protected static final int STATE_COMPLETED = 1;
    protected static final int STATE_FAILED = 2;

    public TaskControllerImpl(TasksClient tasksClient, TaskEventImpl baseEvent)
    {
        this.tasksClient = tasksClient;
        this.baseEvent = baseEvent;
    }

    public void completeTaskWithoutPayload()
    {
        completeTask(null);
    }

    public void completeTask(String newPayload)
    {
        tasksClient.complete(baseEvent)
            .payload(newPayload)
            .execute();

        state = STATE_COMPLETED;
    }

    public void fail(Exception e)
    {
        tasksClient.fail(baseEvent)
            .retries(baseEvent.getRetries() - 1)
            .execute();

        state = STATE_FAILED;
    }

    public boolean isTaskCompleted()
    {
        return state == STATE_COMPLETED;
    }
}
