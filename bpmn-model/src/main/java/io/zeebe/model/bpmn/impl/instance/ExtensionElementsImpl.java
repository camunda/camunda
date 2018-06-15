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

import javax.xml.bind.annotation.XmlElement;

import io.zeebe.model.bpmn.BpmnConstants;
import io.zeebe.model.bpmn.impl.metadata.*;

public class ExtensionElementsImpl
{
    private TaskDefinitionImpl taskDefinition;
    private TaskHeadersImpl taskHeaders;
    private InputOutputMappingImpl inputOutputMapping;

    @XmlElement(name = BpmnConstants.ZEEBE_ELEMENT_TASK_DEFINITION, namespace = BpmnConstants.ZEEBE_NS)
    public void setTaskDefinition(TaskDefinitionImpl taskDefinition)
    {
        this.taskDefinition = taskDefinition;
    }

    public TaskDefinitionImpl getTaskDefinition()
    {
        return taskDefinition;
    }

    @XmlElement(name = BpmnConstants.ZEEBE_ELEMENT_TASK_HEADERS, namespace = BpmnConstants.ZEEBE_NS)
    public void setTaskHeaders(TaskHeadersImpl taskHeaders)
    {
        this.taskHeaders = taskHeaders;
    }

    public TaskHeadersImpl getTaskHeaders()
    {
        return taskHeaders;
    }

    @XmlElement(name = BpmnConstants.ZEEBE_ELEMENT_MAPPING, namespace = BpmnConstants.ZEEBE_NS)
    public void setInputOutputMapping(InputOutputMappingImpl inputOutputMapping)
    {
        this.inputOutputMapping = inputOutputMapping;
    }

    public InputOutputMappingImpl getInputOutputMapping()
    {
        return inputOutputMapping;
    }

}
