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
package io.zeebe.model.bpmn.impl.instance;

import java.util.*;

import javax.xml.bind.annotation.*;

import io.zeebe.model.bpmn.BpmnConstants;
import io.zeebe.model.bpmn.instance.*;
import org.agrona.DirectBuffer;

public class ProcessImpl extends FlowElementImpl implements Workflow
{
    private boolean isExecutable = true;

    private List<SequenceFlowImpl> sequenceFlows = new ArrayList<>();
    private List<StartEventImpl> startEvents = new ArrayList<>();
    private List<EndEventImpl> endEvents = new ArrayList<>();
    private List<ServiceTaskImpl> serviceTasks = new ArrayList<>();
    private List<ExclusiveGatewayImpl> exclusiveGateways = new ArrayList<>();

    private StartEvent initialStartEvent;
    private final List<FlowElement> flowElements = new ArrayList<>();
    private final Map<DirectBuffer, FlowElement> flowElementMap = new HashMap<>();

    @XmlAttribute(name = BpmnConstants.BPMN_ATTRIBUTE_IS_EXECUTABLE)
    public void setExecutable(boolean isExecutable)
    {
        this.isExecutable = isExecutable;
    }

    @Override
    public boolean isExecutable()
    {
        return isExecutable;
    }

    @XmlElement(name = BpmnConstants.BPMN_ELEMENT_SEQUENCE_FLOW, namespace = BpmnConstants.BPMN20_NS)
    public void setSequenceFlows(List<SequenceFlowImpl> sequenceFlows)
    {
        this.sequenceFlows = sequenceFlows;
    }

    public List<SequenceFlowImpl> getSequenceFlows()
    {
        return sequenceFlows;
    }

    @XmlElement(name = BpmnConstants.BPMN_ELEMENT_START_EVENT, namespace = BpmnConstants.BPMN20_NS)
    public void setStartEvents(List<StartEventImpl> startEvents)
    {
        this.startEvents = startEvents;
    }

    public List<StartEventImpl> getStartEvents()
    {
        return startEvents;
    }

    @XmlElement(name = BpmnConstants.BPMN_ELEMENT_END_EVENT, namespace = BpmnConstants.BPMN20_NS)
    public void setEndEvents(List<EndEventImpl> endEvents)
    {
        this.endEvents = endEvents;
    }

    public List<EndEventImpl> getEndEvents()
    {
        return endEvents;
    }

    @XmlElement(name = BpmnConstants.BPMN_ELEMENT_SERVICE_TASK, namespace = BpmnConstants.BPMN20_NS)
    public void setServiceTasks(List<ServiceTaskImpl> serviceTasks)
    {
        this.serviceTasks = serviceTasks;
    }

    public List<ServiceTaskImpl> getServiceTasks()
    {
        return serviceTasks;
    }

    @XmlElement(name = BpmnConstants.BPMN_ELEMENT_EXCLUSIVE_GATEWAY, namespace = BpmnConstants.BPMN20_NS)
    public void setExclusiveGateways(List<ExclusiveGatewayImpl> exclusiveGateways)
    {
        this.exclusiveGateways = exclusiveGateways;
    }

    public List<ExclusiveGatewayImpl> getExclusiveGateways()
    {
        return exclusiveGateways;
    }

    @Override
    public DirectBuffer getBpmnProcessId()
    {
        return getIdAsBuffer();
    }

    @XmlTransient
    public void setInitialStartEvent(StartEvent initialStartEvent)
    {
        this.initialStartEvent = initialStartEvent;
    }

    @Override
    public StartEvent getInitialStartEvent()
    {
        return initialStartEvent;
    }

    @Override
    public List<FlowElement> getFlowElements()
    {
        return flowElements;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends FlowElement> T findFlowElementById(DirectBuffer id)
    {
        return (T) flowElementMap.get(id);
    }

    @Override
    public Map<DirectBuffer, FlowElement> getFlowElementMap()
    {
        return flowElementMap;
    }

    @Override
    public String toString()
    {
        final StringBuilder builder = new StringBuilder();
        builder.append("Workflow [isExecutable=");
        builder.append(isExecutable);
        builder.append(", bpmnProcessId=");
        builder.append(getId());
        builder.append(", name=");
        builder.append(getName());
        builder.append("]");
        return builder.toString();
    }

}
