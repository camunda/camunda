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
package io.zeebe.model.bpmn.builder;

import io.zeebe.model.bpmn.impl.instance.ExtensionElementsImpl;
import io.zeebe.model.bpmn.impl.instance.ServiceTaskImpl;
import io.zeebe.model.bpmn.impl.metadata.*;

public class BpmnServiceTaskBuilder
{
    private final BpmnBuilder builder;
    private final ServiceTaskImpl serviceTask;

    private final ExtensionElementsImpl extensionElements;

    public BpmnServiceTaskBuilder(BpmnBuilder builder, ServiceTaskImpl serviceTask)
    {
        this.builder = builder;
        this.serviceTask = serviceTask;

        this.extensionElements = new ExtensionElementsImpl();
        serviceTask.setExtensionElements(extensionElements);
    }

    public BpmnBuilder done()
    {
        serviceTask.setExtensionElements(extensionElements);
        return builder;
    }

    public BpmnServiceTaskBuilder taskType(String type)
    {
        final TaskDefinitionImpl taskDefinition = getTaskDefinition();
        taskDefinition.setType(type);

        return this;
    }

    public BpmnServiceTaskBuilder taskRetries(int retries)
    {
        final TaskDefinitionImpl taskDefinition = getTaskDefinition();
        taskDefinition.setRetries(retries);

        return this;
    }

    public BpmnServiceTaskBuilder taskHeader(String key, String value)
    {
        final TaskHeaderImpl header = new TaskHeaderImpl();
        header.setKey(key);
        header.setValue(value);

        final TaskHeadersImpl taskHeaders = getTaskHeaders();
        taskHeaders.getTaskHeaders().add(header);

        return this;
    }

    public BpmnServiceTaskBuilder input(String source, String target)
    {
        final MappingImpl inputMapping = new MappingImpl();
        inputMapping.setSource(source);
        inputMapping.setTarget(target);

        final InputOutputMappingImpl inputOutputMappings = getInputOutputMappings();
        inputOutputMappings.getInputs().add(inputMapping);

        return this;
    }

    public BpmnServiceTaskBuilder output(String source, String target)
    {
        final MappingImpl outputMapping = new MappingImpl();
        outputMapping.setSource(source);
        outputMapping.setTarget(target);

        final InputOutputMappingImpl inputOutputMappings = getInputOutputMappings();
        inputOutputMappings.getOutputs().add(outputMapping);

        return this;
    }

    private TaskDefinitionImpl getTaskDefinition()
    {
        TaskDefinitionImpl taskDefinition = extensionElements.getTaskDefinition();
        if (taskDefinition == null)
        {
            taskDefinition = new TaskDefinitionImpl();
            extensionElements.setTaskDefinition(taskDefinition);
        }
        return taskDefinition;
    }

    private TaskHeadersImpl getTaskHeaders()
    {
        TaskHeadersImpl taskHeaders = extensionElements.getTaskHeaders();
        if (taskHeaders == null)
        {
            taskHeaders = new TaskHeadersImpl();
            extensionElements.setTaskHeaders(taskHeaders);
        }
        return taskHeaders;
    }

    private InputOutputMappingImpl getInputOutputMappings()
    {
        InputOutputMappingImpl inputOutputMapping = extensionElements.getInputOutputMapping();
        if (inputOutputMapping == null)
        {
            inputOutputMapping = new InputOutputMappingImpl();
            extensionElements.setInputOutputMapping(inputOutputMapping);
        }
        return inputOutputMapping;
    }

}
