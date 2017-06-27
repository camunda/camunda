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
package io.zeebe.broker.incident.data;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.zeebe.broker.util.msgpack.UnpackedObject;
import io.zeebe.broker.util.msgpack.property.BinaryProperty;
import io.zeebe.broker.util.msgpack.property.EnumProperty;
import io.zeebe.broker.util.msgpack.property.LongProperty;
import io.zeebe.broker.util.msgpack.property.StringProperty;
import io.zeebe.msgpack.spec.MsgPackHelper;

public class IncidentEvent extends UnpackedObject
{
    protected static final DirectBuffer EMPTY_PAYLOAD = new UnsafeBuffer(MsgPackHelper.EMTPY_OBJECT);

    private final EnumProperty<IncidentEventType> eventTypeProp = new EnumProperty<>("eventType", IncidentEventType.class);

    private final EnumProperty<ErrorType> errorTypeProp = new EnumProperty<>("errorType", ErrorType.class, ErrorType.UNKNOWN);
    private final StringProperty errorMessageProp = new StringProperty("errorMessage", "");

    private final LongProperty failureEventPosition = new LongProperty("failureEventPosition", -1L);

    private final StringProperty bpmnProcessIdProp = new StringProperty("bpmnProcessId", "");
    private final LongProperty workflowInstanceKeyProp = new LongProperty("workflowInstanceKey", -1L);
    private final StringProperty activityIdProp = new StringProperty("activityId", "");
    private final LongProperty activityInstanceKeyProp = new LongProperty("activityInstanceKey", -1L);
    private final LongProperty taskKeyProp = new LongProperty("taskKey", -1L);

    private final BinaryProperty payloadProp = new BinaryProperty("payload", EMPTY_PAYLOAD);

    public IncidentEvent()
    {
        this.declareProperty(eventTypeProp)
            .declareProperty(errorTypeProp)
            .declareProperty(errorMessageProp)
            .declareProperty(failureEventPosition)
            .declareProperty(bpmnProcessIdProp)
            .declareProperty(workflowInstanceKeyProp)
            .declareProperty(activityIdProp)
            .declareProperty(activityInstanceKeyProp)
            .declareProperty(taskKeyProp)
            .declareProperty(payloadProp);
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

    public long getFailureEventPosition()
    {
        return failureEventPosition.getValue();
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

    public long getWorkflowInstanceKey()
    {
        return workflowInstanceKeyProp.getValue();
    }

    public IncidentEvent setWorkflowInstanceKey(long workflowInstanceKey)
    {
        this.workflowInstanceKeyProp.setValue(workflowInstanceKey);
        return this;
    }

    public long getActivityInstanceKey()
    {
        return activityInstanceKeyProp.getValue();
    }

    public IncidentEvent setActivityInstanceKey(long activityInstanceKey)
    {
        this.activityInstanceKeyProp.setValue(activityInstanceKey);
        return this;
    }

    public long getTaskKey()
    {
        return taskKeyProp.getValue();
    }

    public IncidentEvent setTaskKey(long taskKey)
    {
        this.taskKeyProp.setValue(taskKey);
        return this;
    }

    public DirectBuffer getPayload()
    {
        return this.payloadProp.getValue();
    }

    public IncidentEvent setPayload(DirectBuffer payload)
    {
        this.payloadProp.setValue(payload);
        return this;
    }
}
