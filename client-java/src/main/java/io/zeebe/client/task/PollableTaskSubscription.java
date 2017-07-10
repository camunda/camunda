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
package io.zeebe.client.task;

/**
 * Represents the subscription to tasks of a certain topic. When a subscription is open,
 * the client continuously receives tasks from the broker. Such tasks can be handled by calling
 * the {@link #poll(TaskHandler)} method.
 *
 * @author Lindhauer
 */
public interface PollableTaskSubscription
{

    /**
     * @return true if this subscription is currently active and tasks are received for it
     */
    boolean isOpen();

    /**
     * Closes the subscription. Blocks until all remaining tasks have been handled.
     */
    void close();

    /**
     * Calls the provided {@link TaskHandler} for a number of tasks that fulfill the subscriptions definition.
     *
     * @return the number of handled tasks
     */
    int poll(TaskHandler taskHandler);
}
