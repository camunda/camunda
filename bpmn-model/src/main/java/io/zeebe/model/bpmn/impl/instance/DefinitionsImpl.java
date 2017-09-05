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
import io.zeebe.model.bpmn.instance.Workflow;
import io.zeebe.model.bpmn.instance.WorkflowDefinition;
import org.agrona.DirectBuffer;

@XmlRootElement(name = BpmnConstants.BPMN_ELEMENT_DEFINITIONS, namespace = BpmnConstants.BPMN20_NS)
public class DefinitionsImpl extends BaseElement implements WorkflowDefinition
{
    private String targetNamespace = "http://zeebe.io/model/bpmn";

    private List<ProcessImpl> processes = new ArrayList<>();

    private Map<DirectBuffer, Workflow> workflowsById = new HashMap<>();

    @XmlElement(name = BpmnConstants.BPMN_ELEMENT_PROCESS, namespace = BpmnConstants.BPMN20_NS)
    public void setProcesses(List<ProcessImpl> processes)
    {
        this.processes = processes;
    }

    public List<ProcessImpl> getProcesses()
    {
        return processes;
    }

    public String getTargetNamespace()
    {
        return targetNamespace;
    }

    @XmlAttribute(name = BpmnConstants.BPMN_ATTRIBUTE_TARGET_NAMESPACE, required = true)
    public void setTargetNamespace(String targetNamespace)
    {
        this.targetNamespace = targetNamespace;
    }

    @Override
    public Workflow getWorklow(DirectBuffer bpmnProcessId)
    {
        return workflowsById.get(bpmnProcessId);
    }

    public Map<DirectBuffer, Workflow> getWorkflowsById()
    {
        return workflowsById;
    }

    @Override
    public Collection<Workflow> getWorkflows()
    {
        return workflowsById.values();
    }

    @Override
    public String toString()
    {
        final StringBuilder builder = new StringBuilder();
        builder.append("WorkflowDefinition [workflows=");
        builder.append(processes);
        builder.append("]");
        return builder.toString();
    }

}
