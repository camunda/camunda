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
package io.zeebe.client.task.cmd;

import java.util.Map;

import io.zeebe.client.cmd.SetPayloadCmd;

/**
 * Update the remaining task retries. If the retries are equal to zero then the
 * task will not be locked again unless the retries are increased.
 */
public interface UpdateTaskRetriesCmd extends SetPayloadCmd<Long, UpdateTaskRetriesCmd>
{
    /**
     * Set the key of the task.
     */
    UpdateTaskRetriesCmd taskKey(long taskKey);

    /**
     * Set the type of the task.
     */
    UpdateTaskRetriesCmd taskType(String taskType);

    /**
     * Sets the given key-value-pairs as the task header.
     */
    UpdateTaskRetriesCmd headers(Map<String, Object> headers);

    /**
     * Sets the remaining retries of the task. The retries must be greater than
     * zero.
     */
    UpdateTaskRetriesCmd retries(int remainingRetries);

}
