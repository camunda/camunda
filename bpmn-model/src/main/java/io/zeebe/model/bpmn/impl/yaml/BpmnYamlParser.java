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
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.zeebe.model.bpmn.builder.BpmnBuilder;
import io.zeebe.model.bpmn.builder.BpmnServiceTaskBuilder;
import io.zeebe.model.bpmn.instance.WorkflowDefinition;

public class BpmnYamlParser
{
    private final BpmnBuilder bpmnBuilder;

    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    public BpmnYamlParser(BpmnBuilder bpmnBuilder)
    {
        this.bpmnBuilder = bpmnBuilder;
    }

    public WorkflowDefinition readFromFile(File file)
    {
        try
        {
            return readFromStream(new FileInputStream(file));
        }
        catch (FileNotFoundException e)
        {
            throw new RuntimeException("Failed to read YAML from file", e);
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
        catch (Exception e)
        {
            throw new RuntimeException("Failed to read YAML model", e);
        }
    }

    private WorkflowDefinition createWorkflow(final YamlDefinitionImpl definition)
    {
        final BpmnBuilder builder = bpmnBuilder.wrap(definition.getName()).startEvent();

        if (isTaskSequence(definition))
        {
            buildTaskSequence(builder, definition);
        }
        else
        {
            buildFlow(builder, definition);
        }

        return builder.done();
    }

    private boolean isTaskSequence(final YamlDefinitionImpl definition)
    {
        return definition.getTasks().stream().allMatch(t -> t.getFlows().isEmpty());
    }

    private void buildTaskSequence(final BpmnBuilder builder, final YamlDefinitionImpl definition)
    {
        for (YamlTask task : definition.getTasks())
        {
            addServiceTask(builder, task);
        }

        builder.endEvent();
    }

    private void addServiceTask(final BpmnBuilder builder, YamlTask task)
    {
        final String id = task.getId();
        final String taskType = task.getType();
        final Integer taskRetries = task.getRetries();

        final BpmnServiceTaskBuilder serviceTaskBuilder = builder.serviceTask(id).taskType(taskType).taskRetries(taskRetries);

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

    private void buildFlow(final BpmnBuilder builder, final YamlDefinitionImpl definition)
    {
        final YamlTask initialTask = definition.getTasks().get(0);

        final Map<String, YamlTask> tasksById = definition.getTasks().stream()
                .collect(Collectors.toMap(YamlTask::getId, Function.identity()));

        addTask(builder, tasksById, new ArrayList<String>(), initialTask.getId());
    }

    private void addTask(final BpmnBuilder builder, final Map<String, YamlTask> tasksById, List<String> createdTasks, final String taskId)
    {
        if (createdTasks.contains(taskId))
        {
            builder.joinWith(taskId);
        }
        else
        {
            final YamlTask task = tasksById.get(taskId);
            if (task == null)
            {
                throw new RuntimeException("No task with id: " + taskId);
            }

            addServiceTask(builder, task);
            createdTasks.add(taskId);

            addFlowFromTask(builder, tasksById, createdTasks, task);
        }
    }

    private void addFlowFromTask(final BpmnBuilder builder, final Map<String, YamlTask> tasksById, List<String> createdTasks, final YamlTask task)
    {
        final List<YamlFlow> flows = task.getFlows();
        if (!flows.isEmpty())
        {
            builder.exclusiveGateway();

            for (YamlFlow flow : flows)
            {
                builder.sequenceFlow(s -> s.condition(flow.getCondition()));

                addTask(builder, tasksById, createdTasks, flow.getNext());
            }

            if (task.getDefaultFlow() != null)
            {
                builder.sequenceFlow(s -> s.defaultFlow());

                addTask(builder, tasksById, createdTasks, task.getDefaultFlow());
            }
        }
        else if (task.getNext() != null)
        {
            builder.sequenceFlow();

            addTask(builder, tasksById, createdTasks, task.getNext());
        }
        else
        {
            builder.endEvent();
        }
    }

}
