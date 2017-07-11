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

import java.time.Instant;
import java.util.Map;

/**
 * Represents a task that was received by a subscription.
 *
 * @author Lindhauer
 */
public interface Task
{
    /**
     * @return the key of the task
     */
    long getKey();

    /**
     * @return the type of the task
     */
    String getType();

    /**
     * @return the time until when the task is locked and can be exclusively
     *         processed by this client.
     */
    Instant getLockExpirationTime();

    /**
     * @return the payload of the task as JSON string
     */
    String getPayload();

    /**
     * Sets the new payload of task. Note that this overrides the existing
     * payload.
     *
     * @param newPayload
     *            the new payload of the task as JSON string
     */
    void setPayload(String newPayload);

    /**
     * @return the headers of the task. This can be additional information about
     *         the task, the related workflow instance or custom data.
     */
    Map<String, Object> getHeaders();

    /**
     * Mark the task as complete. This may continue the workflow instance if the
     * task belongs to one.
     */
    void complete();

    /**
     * Sets the new payload of task and mark it as complete. This may overrides
     * the existing payload and continue the workflow instance if the task
     * belongs to one.
     *
     * @param newPayload
     *            the new payload of the task as JSON string
     */
    void complete(String newPayload);
}
