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
package org.camunda.tngp.broker.incident.data;

import org.agrona.DirectBuffer;
import org.camunda.tngp.broker.util.msgpack.UnpackedObject;
import org.camunda.tngp.broker.util.msgpack.property.EnumProperty;
import org.camunda.tngp.broker.util.msgpack.property.IntegerProperty;
import org.camunda.tngp.broker.util.msgpack.property.LongProperty;
import org.camunda.tngp.broker.util.msgpack.property.StringProperty;

public class IncidentEvent extends UnpackedObject
{
    private final EnumProperty<IncidentEventType> eventTypeProp = new EnumProperty<>("eventType", IncidentEventType.class);

    private final EnumProperty<ErrorType> errorTypeProp = new EnumProperty<>("errorType", ErrorType.class, ErrorType.UNKNOWN);
    private final StringProperty errorMessageProp = new StringProperty("errorMessage");

    private final StringProperty failureEventTopicName = new StringProperty("failureEventTopicName");
    private final IntegerProperty failureEventPartitionId = new IntegerProperty("failureEventPartitionId");
    private final LongProperty failureEventPosition = new LongProperty("failureEventPosition");

    private final StringProperty bpmnProcessIdProp = new StringProperty("bpmnProcessId", "");
    private final LongProperty workflowInstanceKeyProp = new LongProperty("workflowInstanceKey", -1L);
    private final StringProperty activityIdProp = new StringProperty("activityId", "");

    public IncidentEvent()
    {
        this.declareProperty(eventTypeProp)
            .declareProperty(errorTypeProp)
            .declareProperty(errorMessageProp)
            .declareProperty(failureEventTopicName)
            .declareProperty(failureEventPartitionId)
            .declareProperty(failureEventPosition)
            .declareProperty(bpmnProcessIdProp)
            .declareProperty(workflowInstanceKeyProp)
            .declareProperty(activityIdProp);
    }

    public IncidentEventType getEventType()
    {
        return eventTypeProp.getValue();
    }

    public IncidentEvent setEventType(IncidentEventType eventType)
    {
        this.eventTypeProp.setValue(eventType);
        return this;
    }

    public ErrorType getErrorType()
    {
        return errorTypeProp.getValue();
    }

    public IncidentEvent setErrorType(ErrorType errorType)
    {
        this.errorTypeProp.setValue(errorType);
        return this;
    }

    public DirectBuffer getErrorMessage()
    {
        return errorMessageProp.getValue();
    }

    public IncidentEvent setErrorMessage(String errorMessage)
    {
        this.errorMessageProp.setValue(errorMessage);
        return this;
    }

    public DirectBuffer getFailureEventTopicName()
    {
        return failureEventTopicName.getValue();
    }

    public IncidentEvent setFailureEventTopicName(DirectBuffer topicName)
    {
        this.failureEventTopicName.setValue(topicName);
        return this;
    }

    public int getFailureEventPartitionId()
    {
        return failureEventPartitionId.getValue();
    }

    public IncidentEvent setFailureEventPartitionId(int failureEventStreamId)
    {
        this.failureEventPartitionId.setValue(failureEventStreamId);
        return this;
    }

    public long getFailureEventPosition()
    {
        return this.getFailureEventPosition();
    }

    public IncidentEvent setFailureEventPosition(long failureEventPosition)
    {
        this.failureEventPosition.setValue(failureEventPosition);
        return this;
    }

    public DirectBuffer getBpmnProcessId()
    {
        return bpmnProcessIdProp.getValue();
    }

    public IncidentEvent setBpmnProcessId(DirectBuffer directBuffer)
    {
        bpmnProcessIdProp.setValue(directBuffer, 0, directBuffer.capacity());
        return this;
    }

    public DirectBuffer getActivityId()
    {
        return activityIdProp.getValue();
    }

    public IncidentEvent setActivityId(DirectBuffer activityId)
    {
        this.activityIdProp.setValue(activityId, 0, activityId.capacity());
        return this;
    }

    public Long getWorkflowInstanceKey()
    {
        return workflowInstanceKeyProp.getValue();
    }

    public IncidentEvent setWorkflowInstanceKey(long workflowInstanceKey)
    {
        this.workflowInstanceKeyProp.setValue(workflowInstanceKey);
        return this;
    }
}
