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

import io.zeebe.client.event.WorkflowEvent;

public class WorkflowEventImpl implements WorkflowEvent
{
    private String eventType;
    private String bpmnProcessId;
    private int version;
    private String bpmnXml;
    private long deploymentKey;

    @Override
    public String getEventType()
    {
        return eventType;
    }

    public void setEventType(String eventType)
    {
        this.eventType = eventType;
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
        return bpmnXml;
    }

    public void setBpmnXml(String bpmnXml)
    {
        this.bpmnXml = bpmnXml;
    }

    @Override
    public String toString()
    {
        final StringBuilder builder = new StringBuilder();
        builder.append("WorkflowEventImpl [eventType=");
        builder.append(eventType);
        builder.append(", bpmnProcessId=");
        builder.append(bpmnProcessId);
        builder.append(", version=");
        builder.append(version);
        builder.append(", deploymentKey=");
        builder.append(deploymentKey);
        builder.append(", bpmnXml=");
        builder.append(bpmnXml);
        builder.append("]");
        return builder.toString();
    }


}
