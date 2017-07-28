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
package io.zeebe.client.workflow.impl;

import io.zeebe.client.workflow.cmd.WorkflowDefinition;

public class WorkflowDefinitionImpl implements WorkflowDefinition
{
    private String bpmnProcessId;
    private int version;

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

    public WorkflowDefinitionImpl setBpmnProcessId(String bpmnProcessId)
    {
        this.bpmnProcessId = bpmnProcessId;
        return this;
    }

    public WorkflowDefinitionImpl setVersion(int version)
    {
        this.version = version;
        return this;
    }

    @Override
    public String toString()
    {
        final StringBuilder builder = new StringBuilder();
        builder.append("WorkflowDefinition [bpmnProcessId=");
        builder.append(bpmnProcessId);
        builder.append(", version=");
        builder.append(version);
        builder.append("]");
        return builder.toString();
    }
}
