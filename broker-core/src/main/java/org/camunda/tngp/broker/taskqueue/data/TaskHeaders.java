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
package org.camunda.tngp.broker.taskqueue.data;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.broker.util.msgpack.UnpackedObject;
import org.camunda.tngp.broker.util.msgpack.property.ArrayProperty;
import org.camunda.tngp.broker.util.msgpack.property.IntegerProperty;
import org.camunda.tngp.broker.util.msgpack.property.LongProperty;
import org.camunda.tngp.broker.util.msgpack.property.StringProperty;
import org.camunda.tngp.broker.util.msgpack.value.ArrayValue;
import org.camunda.tngp.broker.util.msgpack.value.ArrayValueIterator;
import org.camunda.tngp.msgpack.spec.MsgPackHelper;

import static org.camunda.tngp.broker.workflow.data.WorkflowInstanceEvent.PROP_WORKFLOW_ACTIVITY_ID;
import static org.camunda.tngp.broker.workflow.data.WorkflowInstanceEvent.PROP_WORKFLOW_BPMN_PROCESS_ID;
import static org.camunda.tngp.broker.workflow.data.WorkflowInstanceEvent.PROP_WORKFLOW_INSTANCE_KEY;

public class TaskHeaders extends UnpackedObject
{
    private static final String EMPTY_STRING = "";
    private static final DirectBuffer EMPTY_ARRAY = new UnsafeBuffer(MsgPackHelper.EMPTY_ARRAY);

    private final LongProperty workflowInstanceKeyProp = new LongProperty(PROP_WORKFLOW_INSTANCE_KEY, -1L);
    private final StringProperty bpmnProcessIdProp = new StringProperty(PROP_WORKFLOW_BPMN_PROCESS_ID, EMPTY_STRING);
    private final IntegerProperty workflowDefinitionVersionProp = new IntegerProperty("workflowDefinitionVersion", -1);
    private final StringProperty activityIdProp = new StringProperty(PROP_WORKFLOW_ACTIVITY_ID, EMPTY_STRING);
    private final LongProperty activityInstanceKeyProp = new LongProperty("activityInstanceKey", -1L);

    private final ArrayProperty<TaskHeader> customHeadersProp = new ArrayProperty<>(
            "customHeaders",
            new ArrayValue<>(),
            new ArrayValue<>(EMPTY_ARRAY, 0, EMPTY_ARRAY.capacity()),
            new TaskHeader());

    public TaskHeaders()
    {
        this.declareProperty(bpmnProcessIdProp)
            .declareProperty(workflowDefinitionVersionProp)
            .declareProperty(workflowInstanceKeyProp)
            .declareProperty(activityIdProp)
            .declareProperty(activityInstanceKeyProp)
            .declareProperty(customHeadersProp);
    }

    public long getWorkflowInstanceKey()
    {
        return workflowInstanceKeyProp.getValue();
    }

    public TaskHeaders setWorkflowInstanceKey(long key)
    {
        this.workflowInstanceKeyProp.setValue(key);
        return this;
    }

    public DirectBuffer getActivityId()
    {
        return activityIdProp.getValue();
    }

    public TaskHeaders setActivityId(DirectBuffer activityId)
    {
        return setActivityId(activityId, 0, activityId.capacity());
    }

    public TaskHeaders setActivityId(DirectBuffer activityId, int offset, int length)
    {
        this.activityIdProp.setValue(activityId, offset, length);
        return this;
    }

    public TaskHeaders setBpmnProcessId(DirectBuffer bpmnProcessId)
    {
        this.bpmnProcessIdProp.setValue(bpmnProcessId, 0, bpmnProcessId.capacity());
        return this;
    }

    public DirectBuffer getBpmnProcessId()
    {
        return bpmnProcessIdProp.getValue();
    }

    public int getWorkflowDefinitionVersion()
    {
        return workflowDefinitionVersionProp.getValue();
    }

    public TaskHeaders setWorkflowDefinitionVersion(int version)
    {
        this.workflowDefinitionVersionProp.setValue(version);
        return this;
    }

    public long getActivityInstanceKey()
    {
        return activityInstanceKeyProp.getValue();
    }

    public TaskHeaders setActivityInstanceKey(long activityInstanceKey)
    {
        this.activityInstanceKeyProp.setValue(activityInstanceKey);
        return this;
    }

    public ArrayValueIterator<TaskHeader> customHeaders()
    {
        return customHeadersProp;
    }

}
