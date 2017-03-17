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
package org.camunda.tngp.broker.workflow.data;

import org.agrona.DirectBuffer;
import org.camunda.tngp.broker.util.msgpack.UnpackedObject;
import org.camunda.tngp.broker.util.msgpack.property.IntegerProperty;
import org.camunda.tngp.broker.util.msgpack.property.StringProperty;

public class DeployedWorkflow extends UnpackedObject
{
    private final StringProperty processIdProp = new StringProperty("processId");
    private final IntegerProperty versionProp = new IntegerProperty("version");

    public DeployedWorkflow()
    {
        this.declareProperty(processIdProp)
            .declareProperty(versionProp);
    }

    public DirectBuffer getProcessId()
    {
        return processIdProp.getValue();
    }

    public DeployedWorkflow setProcessId(String processId)
    {
        this.processIdProp.setValue(processId);
        return this;
    }

    public DeployedWorkflow setProcessId(DirectBuffer processId, int offset, int length)
    {
        this.processIdProp.setValue(processId, offset, length);
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
