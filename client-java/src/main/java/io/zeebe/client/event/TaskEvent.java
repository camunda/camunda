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
package io.zeebe.client.event;

import java.time.Instant;
import java.util.Map;

/**
 * POJO representing an event of type {@link TopicEventType#TASK}.
 */
public interface TaskEvent
{

    /**
     * @return the task's type
     */
    String getType();

    /**
     * @return the name of the type in the task's event lifecycle
     */
    String getEventType();

    /**
     * @return headers associated with this task
     */
    Map<String, Object> getHeaders();

    /**
     * @return the lock owner
     */
    String getLockOwner();

    /**
     * @return remaining retries
     */
    Integer getRetries();

    /**
     * @return the time until when the task is locked
     *   and can be exclusively processed by this client.
     */
    Instant getLockExpirationTime();

    /**
     * @return JSON-formatted payload
     */
    String getPayload();
}
