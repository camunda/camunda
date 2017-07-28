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
package io.zeebe.client.event.impl;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.zeebe.client.event.TopicEventType;
import io.zeebe.client.event.WorkflowEvent;

public class WorkflowEventImpl extends EventImpl implements WorkflowEvent
{

    private String bpmnProcessId;
    private int version;
    private byte[] bpmnXml;
    private long deploymentKey;

    @JsonCreator
    public WorkflowEventImpl(@JsonProperty("state") String state)
    {
        super(TopicEventType.WORKFLOW, state);
    }

    @Override
    public String getBpmnProcessId()
    {
        return bpmnProcessId;
    }

    public void setBpmnProcessId(String bpmnProcessId)
    {
        this.bpmnProcessId = bpmnProcessId;
    }

    @Override
    public int getVersion()
    {
        return version;
    }

    public void setVersion(int version)
    {
        this.version = version;
    }

    @Override
    public long getDeploymentKey()
    {
        return deploymentKey;
    }

    public void setDeploymentKey(long deploymentKey)
    {
        this.deploymentKey = deploymentKey;
    }

    @Override
    public String getBpmnXml()
    {
        return new String(bpmnXml, UTF_8);
    }

    public void setBpmnXml(byte[] bpmnXml)
    {
        this.bpmnXml = bpmnXml;
    }

    @Override
    public String toString()
    {
        final StringBuilder builder = new StringBuilder();
        builder.append("WorkflowEvent [state=");
        builder.append(state);
        builder.append(", bpmnProcessId=");
        builder.append(bpmnProcessId);
        builder.append(", version=");
        builder.append(version);
        builder.append(", deploymentKey=");
        builder.append(deploymentKey);
        builder.append(", bpmnXml=");
        builder.append(getBpmnXml());
        builder.append("]");
        return builder.toString();
    }


}
