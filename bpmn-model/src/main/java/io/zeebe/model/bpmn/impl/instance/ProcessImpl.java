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
package io.zeebe.model.bpmn.impl.instance;

import java.util.*;

import javax.xml.bind.annotation.*;

import io.zeebe.model.bpmn.BpmnConstants;
import io.zeebe.model.bpmn.instance.*;
import org.agrona.DirectBuffer;

public class ProcessImpl extends FlowElementImpl implements Workflow
{
    private boolean isExecutable = false;

    private List<SequenceFlowImpl> sequenceFlows = new ArrayList<>();
    private List<StartEventImpl> startEvents = new ArrayList<>();
    private List<EndEventImpl> endEvents = new ArrayList<>();
    private List<ServiceTaskImpl> serviceTasks = new ArrayList<>();

    private StartEvent initialStartEvent;
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
    public FlowElement findFlowElementById(DirectBuffer id)
    {
        return flowElementMap.get(id);
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
