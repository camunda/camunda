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
package io.zeebe.client.task.cmd;

import java.io.InputStream;
import java.util.Map;

import io.zeebe.client.cmd.Request;
import io.zeebe.client.event.TaskEvent;

public interface CreateTaskCommand extends Request<TaskEvent>
{
    int DEFAULT_RETRIES = 3;

    /**
     * Add the given key-value-pair to the task header.
     */
    CreateTaskCommand addCustomHeader(String key, Object value);

    /**
     * Set the given key-value-pairs as the task headers.
     */
    CreateTaskCommand setCustomHeaders(Map<String, Object> headers);

    /**
     * Sets the initial retries of the task. Default is {@value #DEFAULT_RETRIES}.
     */
    CreateTaskCommand retries(int retries);

    /**
     * Set the payload of the command as JSON stream.
     */
    CreateTaskCommand payload(InputStream payload);

    /**
     * Set the payload of the command as JSON string.
     */
    CreateTaskCommand payload(String payload);

}