/*
 * Zeebe Workflow Engine
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
package io.zeebe.engine.processor.workflow.deployment.model.yaml;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import io.zeebe.model.bpmn.builder.ExclusiveGatewayBuilder;
import io.zeebe.model.bpmn.builder.ServiceTaskBuilder;
import io.zeebe.model.bpmn.builder.StartEventBuilder;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class BpmnYamlParser {

  private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

  private YamlDefinitionImpl definition;
  private final Map<String, YamlTask> tasksById = new HashMap<>();
  private final List<String> createdTasks = new ArrayList<>();

  public BpmnModelInstance readFromStream(InputStream inputStream) {
    final YamlDefinitionImpl definition;
    try {
      definition = mapper.readValue(inputStream, YamlDefinitionImpl.class);
    } catch (Exception e) {
      throw new RuntimeException("Unexpected error trying to read BPMN YAML model", e);
    }

    return createWorkflow(definition);
  }

  private BpmnModelInstance createWorkflow(final YamlDefinitionImpl definition) {
    this.definition = definition;

    createdTasks.clear();

    tasksById.clear();
    for (YamlTask task : definition.getTasks()) {
      tasksById.put(task.getId(), task);
    }

    final StartEventBuilder builder =
        Bpmn.createExecutableProcess(definition.getName()).startEvent();

    final YamlTask initialTask = definition.getTasks().get(0);

    addTask(builder, initialTask.getId());

    return builder.done();
  }

  private void addTask(AbstractFlowNodeBuilder<?, ?> builder, final String taskId) {
    if (createdTasks.contains(taskId)) {
      builder.connectTo(taskId);
    } else {
      final YamlTask task = tasksById.get(taskId);
      if (task == null) {
        throw new RuntimeException(
            String.format(
                "Expected to add task with id '%s', but no task definition with that id exists",
                taskId));
      }

      builder = addServiceTask(builder, task);
      createdTasks.add(taskId);

      addFlowFromTask(builder, task);
    }
  }

  private void addFlowFromTask(final AbstractFlowNodeBuilder<?, ?> builder, final YamlTask task) {
    if (!task.getCases().isEmpty()) {
      final String gatewayId = "split-" + task.getId();

      final ExclusiveGatewayBuilder gatewayBuilder = builder.exclusiveGateway(gatewayId);

      for (YamlCase flow : task.getCases()) {
        if (flow.getDefaultCase() != null) {

          gatewayBuilder.defaultFlow();

          addTask(gatewayBuilder, flow.getDefaultCase());
        } else {
          gatewayBuilder.condition(flow.getCondition());

          addTask(gatewayBuilder, flow.getNext());
        }
      }
    } else if (task.getNext() != null) {
      addTask(builder, task.getNext());
    } else {
      final YamlTask nextTask = getNextTask(task);

      if (!task.isEnd() && nextTask != null) {
        addTask(builder, nextTask.getId());
      } else {
        builder.endEvent();
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

  private ServiceTaskBuilder addServiceTask(
      final AbstractFlowNodeBuilder<?, ?> builder, YamlTask task) {
    final String id = task.getId();
    final String taskType = task.getType();
    final int taskRetries = task.getRetries();

    final ServiceTaskBuilder serviceTaskBuilder =
        builder.serviceTask(id).zeebeTaskType(taskType).zeebeTaskRetries(taskRetries);

    for (Entry<String, String> header : task.getHeaders().entrySet()) {
      serviceTaskBuilder.zeebeTaskHeader(header.getKey(), header.getValue());
    }

    addInputOutputMappingToTask(task, serviceTaskBuilder);

    return serviceTaskBuilder;
  }

  private void addInputOutputMappingToTask(YamlTask task, ServiceTaskBuilder serviceTaskBuilder) {
    for (YamlMapping inputMapping : task.getInputs()) {
      serviceTaskBuilder.zeebeInput(inputMapping.getSource(), inputMapping.getTarget());
    }

    for (YamlMapping outputMapping : task.getOutputs()) {
      serviceTaskBuilder.zeebeOutput(outputMapping.getSource(), outputMapping.getTarget());
    }
  }
}
