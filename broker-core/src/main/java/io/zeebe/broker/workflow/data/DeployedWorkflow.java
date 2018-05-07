/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.workflow.data;

import static io.zeebe.broker.workflow.data.WorkflowInstanceEvent.*;

import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.msgpack.property.*;
import org.agrona.DirectBuffer;

public class DeployedWorkflow extends UnpackedObject
{
    private final StringProperty bpmnProcessIdProp = new StringProperty(PROP_WORKFLOW_BPMN_PROCESS_ID);
    private final IntegerProperty versionProp = new IntegerProperty(PROP_WORKFLOW_VERSION);
    private final LongProperty keyProp = new LongProperty(PROP_WORKFLOW_KEY);

    public DeployedWorkflow()
    {
        this.declareProperty(bpmnProcessIdProp)
            .declareProperty(versionProp)
            .declareProperty(keyProp);
    }

    public DirectBuffer getBpmnProcessId()
    {
        return bpmnProcessIdProp.getValue();
    }

    public DeployedWorkflow setBpmnProcessId(DirectBuffer bpmnProcessId)
    {
        return setBpmnProcessId(bpmnProcessId, 0, bpmnProcessId.capacity());
    }

    public DeployedWorkflow setBpmnProcessId(DirectBuffer bpmnProcessId, int offset, int length)
    {
        this.bpmnProcessIdProp.setValue(bpmnProcessId, offset, length);
        return this;
    }

    public int getVersion()
    {
        return versionProp.getValue();
    }

    public DeployedWorkflow setVersion(int version)
    {
        this.versionProp.setValue(version);
        return this;
    }

    public long getKey()
    {
        return keyProp.getValue();
    }

    public DeployedWorkflow setKey(long key)
    {
        this.keyProp.setValue(key);
        return this;
    }

}
