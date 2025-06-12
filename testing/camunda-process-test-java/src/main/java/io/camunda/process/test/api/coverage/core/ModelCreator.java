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
import io.camunda.process.test.api.coverage.model.Model;
import io.camunda.process.test.impl.assertions.CamundaDataSource;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.FlowNode;
import io.camunda.zeebe.model.bpmn.instance.Process;
import io.camunda.zeebe.model.bpmn.instance.SequenceFlow;
import java.io.ByteArrayInputStream;
import java.util.Set;
import java.util.stream.Collectors;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;

/**
 * Utility class for creating process models from Camunda engine definitions.
 *
 * <p>This class provides functionality to retrieve BPMN models from the Camunda engine, parse their
 * structure, and create Model objects that contain information about executable elements and
 * sequence flows for coverage analysis.
 */
public class ModelCreator {

  /**
   * Creates a model object from a process definition in the Camunda engine.
   *
   * <p>Retrieves the BPMN XML for the specified process definition, parses it to extract flow nodes
   * and sequence flows, and calculates the total number of executable elements for coverage
   * analysis.
   *
   * @param dataSource The data source to retrieve process definition data
   * @param processDefinitionId The ID of the process definition to create a model for
   * @return A Model object containing process structure information and element counts
   * @throws IllegalArgumentException if the model cannot be read from the process definition
   */
  public static Model createModel(
      final CamundaDataSource dataSource, final String processDefinitionId) {
    final ByteArrayInputStream inputStream =
        new ByteArrayInputStream(
            dataSource
                .getProcessDefinitionXmlByProcessDefinitionId(processDefinitionId)
                .getBytes());
    final BpmnModelInstance modelInstance = Bpmn.readModelFromStream(inputStream);

    if (modelInstance == null) {
      throw new IllegalArgumentException(
          "Cannot read model from process definition: " + processDefinitionId);
    }

    final ProcessDefinition processDefinition =
        dataSource.findProcessDefinitionByProcessDefinitionId(processDefinitionId);

    final Set<FlowNode> definitionFlowNodes =
        modelInstance.getModelElementsByType(FlowNode.class).stream()
            .filter(node -> isExecutable(node, processDefinition.getProcessDefinitionId()))
            .collect(Collectors.toSet());

    final Set<SequenceFlow> definitionSequenceFlows =
        modelInstance.getModelElementsByType(SequenceFlow.class).stream()
            .filter(s -> definitionFlowNodes.contains(s.getSource()))
            .collect(Collectors.toSet());

    return new Model(
        processDefinition.getProcessDefinitionId(),
        definitionFlowNodes.size() + definitionSequenceFlows.size(),
        String.valueOf(processDefinition.getVersion()),
        Bpmn.convertToString(modelInstance));
  }

  /**
   * Determines if a model element is part of an executable process.
   *
   * <p>Checks if the element belongs to the process with the specified ID and whether that process
   * is marked as executable in the BPMN definition.
   *
   * @param node The model element to check
   * @param processId The ID of the process being analyzed
   * @return true if the element is part of an executable process, false otherwise
   */
  private static boolean isExecutable(final ModelElementInstance node, final String processId) {
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
}
