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
package io.zeebe.client.api.record;

import java.util.Map;

public interface WorkflowInstanceRecord extends Record
{
    /**
     * @return the BPMN process id this workflow instance belongs to.
     */
    String getBpmnProcessId();

    /**
     * @return the version of the deployed workflow this instance belongs to.
     */
    int getVersion();

    /**
     * @return the key of the deployed workflow this instance belongs to.
     */
    long getWorkflowKey();

    /**
     * @return the key of the workflow instance
     */
    long getWorkflowInstanceKey();

    /**
     * @return the id of the current activity, or empty if the event does not
     *         belong to an activity.
     */
    String getActivityId();

    /**
     * @return the payload of the workflow instance as JSON-formatted string.
     */
    String getPayload();

    /**
     * @return the de-serialized payload of the workflow instance as map
     */
    Map<String, Object> getPayloadAsMap();

    /**
     * @return de-serialized payload of the workflow instance as the given type
     */
    <T> T getPayloadAsType(Class<T> payloadType);
}
