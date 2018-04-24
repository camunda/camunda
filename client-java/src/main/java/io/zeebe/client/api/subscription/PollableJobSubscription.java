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
package io.zeebe.client.api.subscription;

/**
 * Represents the subscription to work items of a certain type. When a subscription is open,
 * the client continuously receives work items from the broker. Such items can be handled by calling
 * the {@link #poll(JobHandler)} method.
 */
public interface PollableJobSubscription
{

    /**
     * @return true if this subscription is currently active and work items are received for it
     */
    boolean isOpen();

    /**
     * Closes the subscription. Blocks until all remaining work items have been handled.
     */
    void close();

    /**
     * Calls the provided {@link JobHandler} for a number of tasks that fulfill the subscriptions definition.
     *
     * @return the number of handled work items
     */
    int poll(JobHandler workItemHandler);
}
