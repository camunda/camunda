/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.tngp.client.event.impl;

import org.camunda.tngp.client.event.IncidentEvent;

public class IncidentEventImpl implements IncidentEvent
{
    private String eventType;

    private String errorType;
    private String errorMessage;

    private String bpmnProcessId;
    private long workflowInstanceKey;
    private String activityId;
    private long activityInstanceKey;

    private long taskKey;

    public String getEventType()
    {
        return eventType;
    }

    public void setEventType(String eventType)
    {
        this.eventType = eventType;
    }

    public String getErrorType()
    {
        return errorType;
    }

    public void setErrorType(String errorType)
    {
        this.errorType = errorType;
    }

    public String getErrorMessage()
    {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage)
    {
        this.errorMessage = errorMessage;
    }

    public String getBpmnProcessId()
    {
        return bpmnProcessId;
    }

    public void setBpmnProcessId(String bpmnProcessId)
    {
        this.bpmnProcessId = bpmnProcessId;
    }

    public String getActivityId()
    {
        return activityId;
    }

    public void setActivityId(String activityId)
    {
        this.activityId = activityId;
    }

    public long getActivityInstanceKey()
    {
        return activityInstanceKey;
    }

    public void setActivityInstanceKey(long activityInstanceKey)
    {
        this.activityInstanceKey = activityInstanceKey;
    }

    public long getTaskKey()
    {
        return taskKey;
    }

    public void setTaskKey(long taskKey)
    {
        this.taskKey = taskKey;
    }

    public long getWorkflowInstanceKey()
    {
        return workflowInstanceKey;
    }

    public void setWorkflowInstanceKey(long workflowInstanceKey)
    {
        this.workflowInstanceKey = workflowInstanceKey;
    }

    @Override
    public String toString()
    {
        final StringBuilder builder = new StringBuilder();
        builder.append("IncidentEventImpl [eventType=");
        builder.append(eventType);
        builder.append(", errorType=");
        builder.append(errorType);
        builder.append(", errorMessage=");
        builder.append(errorMessage);
        builder.append(", bpmnProcessId=");
        builder.append(bpmnProcessId);
        builder.append(", workflowInstanceKey=");
        builder.append(workflowInstanceKey);
        builder.append(", activityId=");
        builder.append(activityId);
        builder.append(", activityInstanceKey=");
        builder.append(activityInstanceKey);
        builder.append(", taskKey=");
        builder.append(taskKey);
        builder.append("]");
        return builder.toString();
    }

}
