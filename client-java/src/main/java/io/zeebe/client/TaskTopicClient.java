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
package io.zeebe.client;

import io.zeebe.client.task.PollableTaskSubscription;
import io.zeebe.client.task.PollableTaskSubscriptionBuilder;
import io.zeebe.client.task.TaskSubscriptionBuilder;
import io.zeebe.client.task.cmd.CompleteTaskCmd;
import io.zeebe.client.task.cmd.CreateTaskCmd;
import io.zeebe.client.task.cmd.FailTaskCmd;
import io.zeebe.client.task.cmd.UpdateTaskRetriesCmd;

/**
 * The task client api.
 */
public interface TaskTopicClient
{
    /**
     * Create a new task.
     */
    CreateTaskCmd create();

    /**
     * Complete a locked task.
     */
    CompleteTaskCmd complete();

    /**
     * Mark a locked task as failed.
     */
    FailTaskCmd fail();

    /**
     * Update the remaining retries of a task.
     */
    UpdateTaskRetriesCmd updateRetries();

    /**
     * Create a new subscription to lock tasks and execute them by the given
     * handler.
     */
    TaskSubscriptionBuilder newTaskSubscription();

    /**
     * Create a new subscription to lock tasks. Use
     * {@linkplain PollableTaskSubscription#poll(io.zeebe.client.task.TaskHandler)}
     * to execute the locked tasks.
     */
    PollableTaskSubscriptionBuilder newPollableTaskSubscription();

}
