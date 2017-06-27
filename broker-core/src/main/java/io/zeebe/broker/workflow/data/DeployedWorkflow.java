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
package io.zeebe.broker.workflow.data;

import static io.zeebe.broker.workflow.data.WorkflowInstanceEvent.PROP_WORKFLOW_BPMN_PROCESS_ID;
import static io.zeebe.broker.workflow.data.WorkflowInstanceEvent.PROP_WORKFLOW_VERSION;

import org.agrona.DirectBuffer;

import io.zeebe.broker.util.msgpack.UnpackedObject;
import io.zeebe.broker.util.msgpack.property.IntegerProperty;
import io.zeebe.broker.util.msgpack.property.StringProperty;

public class DeployedWorkflow extends UnpackedObject
{
    private final StringProperty bpmnProcessIdProp = new StringProperty(PROP_WORKFLOW_BPMN_PROCESS_ID);
    private final IntegerProperty versionProp = new IntegerProperty(PROP_WORKFLOW_VERSION);

    public DeployedWorkflow()
    {
        this.declareProperty(bpmnProcessIdProp)
            .declareProperty(versionProp);
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

}
