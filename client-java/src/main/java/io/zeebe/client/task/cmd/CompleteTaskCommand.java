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

import io.zeebe.client.cmd.Request;
import io.zeebe.client.event.TaskEvent;

public interface CompleteTaskCommand extends Request<TaskEvent>
{

    /**
     * Set the payload of the command as JSON stream.
     */
    CompleteTaskCommand payload(InputStream payload);

    /**
     * Set the payload of the command as JSON string.
     */
    CompleteTaskCommand payload(String payload);

    /**
     * <p>Complete the task without submitting payload.
     *
     * <p>If this task is executed in the context of
     * workflow, this means no payload is merged from the task into the workflow instance, so
     * the workflow instance payload remains unchanged.
     *
     * <p>If this is a standalone task, this method may or may not be used to
     * alter the payload as it appears on the log. No follow-up processing depends on the payload.
     */
    CompleteTaskCommand withoutPayload();
}
