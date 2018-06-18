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

import static io.zeebe.model.bpmn.impl.validation.nodes.task.InputOutputMappingValidator.OUTPUT_BEHAVIOR_IS_NOT_SUPPORTED_MSG;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.zeebe.model.bpmn.builder.BpmnBuilder;
import io.zeebe.model.bpmn.builder.BpmnServiceTaskBuilder;
import io.zeebe.model.bpmn.impl.error.InvalidModelException;
import io.zeebe.model.bpmn.instance.OutputBehavior;
import io.zeebe.model.bpmn.instance.WorkflowDefinition;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.*;
import java.util.Map.Entry;

public class BpmnYamlParser {
  private final BpmnBuilder bpmnBuilder;

  private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

  private YamlDefinitionImpl definition;
  private final Map<String, YamlTask> tasksById = new HashMap<>();
  private final List<String> createdTasks = new ArrayList<>();

  public BpmnYamlParser(BpmnBuilder bpmnBuilder) {
    this.bpmnBuilder = bpmnBuilder;
  }

  public WorkflowDefinition readFromFile(File file) {
    try {
      return readFromStream(new FileInputStream(file));
    } catch (FileNotFoundException e) {
      throw new RuntimeException("Failed to read YAML from file", e);
    }
  }

  public WorkflowDefinition readFromStream(InputStream inputStream) {
    YamlDefinitionImpl definition = null;
    try {
      definition = mapper.readValue(inputStream, YamlDefinitionImpl.class);
    } catch (Exception e) {
      throw new RuntimeException("Failed to read YAML model", e);
    }

    final WorkflowDefinition workflowDefinition = createWorkflow(definition);

    return workflowDefinition;
  }

  private WorkflowDefinition createWorkflow(final YamlDefinitionImpl definition) {
    this.definition = definition;

    createdTasks.clear();

    tasksById.clear();
    for (YamlTask task : definition.getTasks()) {
      tasksById.put(task.getId(), task);
    }

    final BpmnBuilder builder = bpmnBuilder.wrap(definition.getName()).startEvent();

    final YamlTask initialTask = definition.getTasks().get(0);

    addTask(builder, initialTask.getId());

    return builder.done();
  }

  private void addTask(final BpmnBuilder builder, final String taskId) {
    if (createdTasks.contains(taskId)) {
      builder.joinWith(taskId);
    } else {
      final YamlTask task = tasksById.get(taskId);
      if (task == null) {
        throw new RuntimeException("No task with id: " + taskId);
      }

      addServiceTask(builder, task);
      createdTasks.add(taskId);

      addFlowFromTask(builder, task);
    }
  }

  private void addFlowFromTask(final BpmnBuilder builder, final YamlTask task) {
    if (!task.getCases().isEmpty()) {
      final String gatewayId = "split-" + task.getId();

      builder.exclusiveGateway(gatewayId);

      for (YamlCase flow : task.getCases()) {
        if (flow.getDefaultCase() != null) {
          builder.continueAt(gatewayId);
          builder.sequenceFlow(s -> s.defaultFlow());

          addTask(bpmnBuilder, flow.getDefaultCase());
        } else {
          builder.continueAt(gatewayId);
          builder.sequenceFlow(s -> s.condition(flow.getCondition()));

          addTask(bpmnBuilder, flow.getNext());
        }
      }
    } else if (task.getNext() != null) {
      addTask(bpmnBuilder, task.getNext());
    } else {
      final YamlTask nextTask = getNextTask(task);

      if (!task.isEnd() && nextTask != null) {
        addTask(bpmnBuilder, nextTask.getId());
      } else {
        bpmnBuilder.endEvent();
      }
    }
  }

  private YamlTask getNextTask(YamlTask task) {
    final List<YamlTask> tasks = definition.getTasks();
    final int index = tasks.indexOf(task);

    if (index + 1 < tasks.size()) {
      return tasks.get(index + 1);
    } else {
      return null;
    }
  }

  private void addServiceTask(final BpmnBuilder builder, YamlTask task) {
    final String id = task.getId();
    final String taskType = task.getType();
    final Integer taskRetries = task.getRetries();

    final BpmnServiceTaskBuilder serviceTaskBuilder =
        builder.serviceTask(id).taskType(taskType).taskRetries(taskRetries);

    for (Entry<String, String> header : task.getHeaders().entrySet()) {
      serviceTaskBuilder.taskHeader(header.getKey(), header.getValue());
    }

    addInputOutputMappingToTask(task, serviceTaskBuilder);

    serviceTaskBuilder.done();
  }

  private void addInputOutputMappingToTask(
      YamlTask task, BpmnServiceTaskBuilder serviceTaskBuilder) {
    final String outputBehaviorString = task.getOutputBehavior();
    OutputBehavior outputBehavior = null;
    try {
      outputBehavior = OutputBehavior.valueOf(outputBehaviorString.toUpperCase());
    } catch (IllegalArgumentException ilae) {
      throw new InvalidModelException(
          String.format(
              OUTPUT_BEHAVIOR_IS_NOT_SUPPORTED_MSG,
              outputBehaviorString,
              Arrays.toString(OutputBehavior.values())));
    }
    if (outputBehavior != null) {
      serviceTaskBuilder.outputBehavior(outputBehavior);
    }

    for (YamlMapping inputMapping : task.getInputs()) {
      serviceTaskBuilder.input(inputMapping.getSource(), inputMapping.getTarget());
    }

    for (YamlMapping outputMapping : task.getOutputs()) {
      serviceTaskBuilder.output(outputMapping.getSource(), outputMapping.getTarget());
    }
  }
}
