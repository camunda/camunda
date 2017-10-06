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
package io.zeebe.client.task;

import java.time.Duration;

/**
 * Builds a {@link PollableTaskSubscription} that can be manually polled for execution.
 */
public interface PollableTaskSubscriptionBuilder
{

    /**
     * Sets the task type to subscribe to. Must not be null.
     */
    PollableTaskSubscriptionBuilder taskType(String taskType);

    /**
     * Sets the lock duration for which subscribed tasks will be
     * exclusively locked for this task client.
     *
     * @param lockDuration in milliseconds
     */
    PollableTaskSubscriptionBuilder lockTime(long lockDuration);

    /**
     * Sets the lock duration for which subscribed tasks will be
     * exclusively locked for this task client.
     *
     * @param lockDuration duration for which tasks are being locked
     */
    PollableTaskSubscriptionBuilder lockTime(Duration lockDuration);

    /**
     * Sets the owner for which subscripted tasks will be exclusively locked.
     *
     * @param lockOwner owner of which tasks are being locked
     */
    PollableTaskSubscriptionBuilder lockOwner(String lockOwner);

    /**
     * Sets the number of tasks which will be locked at the same time.
     *
     * @param numTasks number of locked tasks
     */
    PollableTaskSubscriptionBuilder taskFetchSize(int numTasks);

    /**
     * TEMPORARY: Defines the partition to subscribe to.
     * If no partition id is set, opens a subscription to the single existing partition. An exception
     * is thrown if there is no such partition.
     */
    PollableTaskSubscriptionBuilder partitionId(int partition);

    /**
     * Opens a new {@link PollableTaskSubscription}. Begins receiving
     * tasks from that point on.
     */
    PollableTaskSubscription open();
}
