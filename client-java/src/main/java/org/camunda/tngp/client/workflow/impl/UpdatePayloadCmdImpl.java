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
package org.camunda.tngp.client.workflow.impl;

import static org.camunda.tngp.util.EnsureUtil.ensureGreaterThan;
import static org.camunda.tngp.util.EnsureUtil.ensureNotNull;

import java.io.InputStream;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.tngp.client.cmd.ClientCommandRejectedException;
import org.camunda.tngp.client.impl.ClientCmdExecutor;
import org.camunda.tngp.client.impl.cmd.AbstractExecuteCmdImpl;
import org.camunda.tngp.client.impl.data.MsgPackConverter;
import org.camunda.tngp.client.workflow.cmd.UpdatePayloadCmd;
import org.camunda.tngp.protocol.clientapi.EventType;

public class UpdatePayloadCmdImpl extends AbstractExecuteCmdImpl<WorkflowInstanceEvent, Void> implements UpdatePayloadCmd
{
    private static final String ERROR_MESSAGE = "Failed to update payload of the workflow instance with key '%d'.";

    private final WorkflowInstanceEvent workflowInstanceEvent = new WorkflowInstanceEvent();
    private final MsgPackConverter msgPackConverter = new MsgPackConverter();

    private long activityInstanceKey;

    public UpdatePayloadCmdImpl(ClientCmdExecutor cmdExecutor, ObjectMapper objectMapper, String topicName, int partitionId)
    {
        super(cmdExecutor, objectMapper, WorkflowInstanceEvent.class, topicName, partitionId, EventType.WORKFLOW_EVENT);
    }

    @Override
    public UpdatePayloadCmd activityInstanceKey(long activityInstanceKey)
    {
        this.activityInstanceKey = activityInstanceKey;
        return this;
    }

    @Override
    public UpdatePayloadCmd workflowInstanceKey(long workflowInstanceKey)
    {
        this.workflowInstanceEvent.setWorkflowInstanceKey(workflowInstanceKey);
        return this;
    }

    @Override
    public UpdatePayloadCmd payload(InputStream payload)
    {
        this.workflowInstanceEvent.setPayload(msgPackConverter.convertToMsgPack(payload));
        return this;
    }

    @Override
    public UpdatePayloadCmd payload(String payload)
    {
        this.workflowInstanceEvent.setPayload(msgPackConverter.convertToMsgPack(payload));
        return this;
    }

    @Override
    protected Object writeCommand()
    {
        workflowInstanceEvent.setEventType(WorkflowInstanceEventType.UPDATE_PAYLOAD);

        return workflowInstanceEvent;
    }

    @Override
    protected long getKey()
    {
        return activityInstanceKey;
    }

    @Override
    protected void reset()
    {
        activityInstanceKey = -1L;
        workflowInstanceEvent.reset();
    }

    @Override
    protected Void getResponseValue(int channelId, long key, WorkflowInstanceEvent event)
    {
        if (event.getEventType() == WorkflowInstanceEventType.UPDATE_PAYLOAD_REJECTED)
        {
            throw new ClientCommandRejectedException(String.format(ERROR_MESSAGE, event.getWorkflowInstanceKey()));
        }
        return null;
    }

    @Override
    public void validate()
    {
        super.validate();
        ensureGreaterThan("activity instance key", activityInstanceKey, 0);
        ensureGreaterThan("workflow instance key", workflowInstanceEvent.getWorkflowInstanceKey(), 0);
        ensureNotNull("payload", workflowInstanceEvent.getPayload());
    }

}
