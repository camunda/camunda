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
package io.zeebe.model.bpmn.impl.yaml;

import java.io.*;
import java.util.Map.Entry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.builder.BpmnBuilder;
import io.zeebe.model.bpmn.builder.BpmnServiceTaskBuilder;
import io.zeebe.model.bpmn.instance.WorkflowDefinition;

public class BpmnYamlParser
{
    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    public WorkflowDefinition readFromFile(File file)
    {
        try
        {
            return readFromStream(new FileInputStream(file));
        }
        catch (FileNotFoundException e)
        {
            throw new RuntimeException(e);
        }
    }

    public WorkflowDefinition readFromStream(InputStream inputStream)
    {
        try
        {
            final YamlDefinitionImpl definition = mapper.readValue(inputStream, YamlDefinitionImpl.class);
            final WorkflowDefinition workflowDefinition = createWorkflow(definition);

            return workflowDefinition;
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    private WorkflowDefinition createWorkflow(final YamlDefinitionImpl definition)
    {
        // only simple workflow with start event, service tasks* and end event
        final BpmnBuilder builder = Bpmn.createExecutableWorkflow(definition.getName())
                .startEvent();

        for (YamlTask task : definition.getTasks())
        {
            addServiceTask(builder, task);
        }

        return builder.endEvent().done();
    }

    private void addServiceTask(final BpmnBuilder builder, YamlTask task)
    {
        final String id = task.getId();
        final String taskType = task.getType();
        final Integer taskRetries = task.getRetries();

        final BpmnServiceTaskBuilder serviceTaskBuilder = builder
            .serviceTask(id)
            .taskType(taskType)
            .taskRetries(taskRetries);

        for (Entry<String, String> header : task.getHeaders().entrySet())
        {
            serviceTaskBuilder.taskHeader(header.getKey(), header.getValue());
        }

        for (YamlMapping inputMapping : task.getInputs())
        {
            serviceTaskBuilder.input(inputMapping.getSource(), inputMapping.getTarget());
        }

        for (YamlMapping outputMapping : task.getOutputs())
        {
            serviceTaskBuilder.output(outputMapping.getSource(), outputMapping.getTarget());
        }

        serviceTaskBuilder.done();
    }

}
