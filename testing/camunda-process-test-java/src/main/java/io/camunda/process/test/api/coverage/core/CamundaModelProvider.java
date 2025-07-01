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
package io.camunda.process.test.api.coverage.core;

import io.camunda.client.api.search.response.ProcessDefinition;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.coverage.model.Model;
import io.camunda.process.test.impl.assertions.CamundaDataSource;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.FlowNode;
import io.camunda.zeebe.model.bpmn.instance.Process;
import io.camunda.zeebe.model.bpmn.instance.SequenceFlow;
import java.io.ByteArrayInputStream;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;

/** Provider that is used to load process models from the engine. */
public class CamundaModelProvider implements ModelProvider {

  private final CamundaDataSource dataSource;

  public CamundaModelProvider(final CamundaDataSource dataSource) {
    this.dataSource = dataSource;
  }

  @Override
  public Model getModel(final String key) {
    final ProcessDefinition processDefinition =
        dataSource.findProcessDefinitionByProcessDefinitionId(key);

    final ByteArrayInputStream inputStream =
        new ByteArrayInputStream(
            dataSource
                .getProcessDefinitionXmlByProcessDefinitionKey(
                    processDefinition.getProcessDefinitionKey())
                .getBytes());
    final BpmnModelInstance modelInstance = Bpmn.readModelFromStream(inputStream);

    if (modelInstance == null) {
      throw new IllegalArgumentException();
    }

    final Set<FlowNode> definitionFlowNodes =
        getExecutableFlowNodes(modelInstance.getModelElementsByType(FlowNode.class), key);
    final Set<SequenceFlow> definitionSequenceFlows =
        getExecutableSequenceNodes(
            modelInstance.getModelElementsByType(SequenceFlow.class), definitionFlowNodes);

    return new Model(
        key,
        definitionFlowNodes.size() + definitionSequenceFlows.size(),
        String.valueOf(processDefinition.getVersion()),
        Bpmn.convertToString(modelInstance));
  }

  private Set<FlowNode> getExecutableFlowNodes(
      final Collection<FlowNode> flowNodes, final String processId) {
    return flowNodes.stream()
        .filter(node -> isExecutable(node, processId))
        .collect(Collectors.toSet());
  }

  private Set<SequenceFlow> getExecutableSequenceNodes(
      final Collection<SequenceFlow> sequenceFlows, final Set<FlowNode> definitionFlowNodes) {
    return sequenceFlows.stream()
        .filter(s -> definitionFlowNodes.contains(s.getSource()))
        .collect(Collectors.toSet());
  }

  private boolean isExecutable(final ModelElementInstance node, final String processId) {
    if (node == null) {
      return false;
    }

    if (node instanceof Process) {
      final Process process = (Process) node;
      return process.isExecutable() && process.getId().equals(processId);
    } else {
      return isExecutable(node.getParentElement(), processId);
    }
  }

  private CamundaDataSource createDataSource(final CamundaProcessTestContext context) {
    return new CamundaDataSource(context.createClient());
  }
}
