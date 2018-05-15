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
package io.zeebe.client.impl.event;

import io.zeebe.client.api.commands.Workflow;

public class WorkflowImpl implements Workflow
{
    private String bpmnProcessId;
    private int version;
    private long workflowKey;
    private byte[] bpmnXml;

    @Override
    public String getBpmnProcessId()
    {
        return bpmnProcessId;
    }

    @Override
    public int getVersion()
    {
        return version;
    }

    public WorkflowImpl setBpmnProcessId(String bpmnProcessId)
    {
        this.bpmnProcessId = bpmnProcessId;
        return this;
    }

    public WorkflowImpl setVersion(int version)
    {
        this.version = version;
        return this;
    }

    public void setWorkflowKey(long workflowKey)
    {
        this.workflowKey = workflowKey;
    }

    @Override
    public long getWorkflowKey()
    {
        return workflowKey;
    }

    public byte[] getBpmnXml()
    {
        return bpmnXml;
    }

    public void setBpmnXml(byte[] bpmnXml)
    {
        this.bpmnXml = bpmnXml;
    }

    @Override
    public String toString()
    {
        final StringBuilder builder = new StringBuilder();
        builder.append("Workflow [bpmnProcessId=");
        builder.append(bpmnProcessId);
        builder.append(", version=");
        builder.append(version);
        builder.append("]");
        return builder.toString();
    }
}
