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
package io.zeebe.client;

import io.zeebe.client.event.TaskEvent;
import io.zeebe.client.task.PollableTaskSubscription;
import io.zeebe.client.task.PollableTaskSubscriptionBuilder;
import io.zeebe.client.task.TaskSubscriptionBuilder;
import io.zeebe.client.task.cmd.CompleteTaskCommand;
import io.zeebe.client.task.cmd.CreateTaskCommand;
import io.zeebe.client.task.cmd.FailTaskCommand;
import io.zeebe.client.task.cmd.UpdateTaskRetriesCommand;

/**
 * Provides access to APIs revolving around task events.
 */
public interface TasksClient
{

    /**
     * Create a new task.
     */
    CreateTaskCommand create(String topic, String type);

    /**
     * Complete a locked task.
     *
     * @param event the task to base the request on
     */
    CompleteTaskCommand complete(TaskEvent event);

    /**
     * Mark a locked task as failed.
     *
     * @param event the task to base the request on
     */
    FailTaskCommand fail(TaskEvent event);

    /**
     * Update the remaining retries of a task.
     *
     * @param event the task to base the request on.
     */
    UpdateTaskRetriesCommand updateRetries(TaskEvent event);

    /**
     * Create a new subscription to lock tasks and execute them by the given
     * handler. Task handler invocation is <i>managed</i> by the client library, i.e.
     * the handler is automatically invoked whenever new tasks arrive.
     */
    TaskSubscriptionBuilder newTaskSubscription(String topic);

    /**
     * Create a new subscription to lock tasks. Task handler invocation is
     * <i>not managed</i> by the client library. Call
     * {@linkplain PollableTaskSubscription#poll(io.zeebe.client.task.TaskHandler)}
     * repeatedly to execute the any locked tasks that are currently available.
     */
    PollableTaskSubscriptionBuilder newPollableTaskSubscription(String topic);

}
