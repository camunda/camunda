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
package io.camunda.process.test.impl.coverage.core;

import io.camunda.client.api.search.response.ProcessDefinition;
import io.camunda.process.test.api.coverage.model.ImmutableProcessModel;
import io.camunda.process.test.api.coverage.model.ProcessModel;
import io.camunda.process.test.impl.coverage.data.CoverageProcessDefinitionData;
import io.camunda.process.test.impl.coverage.data.CoverageTestData;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.FlowNode;
import io.camunda.zeebe.model.bpmn.instance.Process;
import io.camunda.zeebe.model.bpmn.instance.SequenceFlow;
import java.io.ByteArrayInputStream;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
   * @param testResults The data source to retrieve process definition data
   * @param processDefinitionId The ID of the process definition to create a model for
   * @return A Model object containing process structure information and element counts
   * @throws IllegalArgumentException if the model cannot be read from the process definition
   */
  public static ProcessModel createModel(
      final CoverageTestData testResults, final String processDefinitionId) {

    final CoverageProcessDefinitionData processDefinitionData =
        testResults.getProcessDefinitionData().stream()
            .filter(
                data ->
                    data.getProcessDefinition()
                        .getProcessDefinitionId()
                        .equals(processDefinitionId))
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "No process definition data found for ID: " + processDefinitionId));

    final BpmnModelInstance modelInstance =
        readModel(processDefinitionData.getXml(), processDefinitionId);

    final ProcessDefinition processDefinition = processDefinitionData.getProcessDefinition();

    return ImmutableProcessModel.builder()
        .processDefinitionId(processDefinition.getProcessDefinitionId())
        .processName(processDefinition.getName())
        .totalElementCount(
            coverableElementIds(modelInstance, processDefinition.getProcessDefinitionId()).size())
        .version(String.valueOf(processDefinition.getVersion()))
        .xml(Bpmn.convertToString(modelInstance))
        .build();
  }

  /**
   * Collects the ids of the elements a model can cover, which are the elements counted by {@link
   * #createModel}.
   *
   * @param processModel The model to collect the element ids of
   * @return The ids of the flow nodes and sequence flows of the executable process
   */
  public static Set<String> coverableElementIds(final ProcessModel processModel) {
    return coverableElementIds(
        readModel(processModel.getXml(), processModel.getProcessDefinitionId()),
        processModel.getProcessDefinitionId());
  }

  /**
   * Collects the ids of the elements a model can cover, which are the elements counted by {@link
   * #createModel}.
   *
   * @param modelInstance The parsed BPMN model
   * @param processDefinitionId The ID of the executable process within the model
   * @return The ids of the flow nodes and sequence flows of the executable process
   */
  public static Set<String> coverableElementIds(
      final BpmnModelInstance modelInstance, final String processDefinitionId) {

    final Set<FlowNode> definitionFlowNodes =
        modelInstance.getModelElementsByType(FlowNode.class).stream()
            .filter(node -> isExecutable(node, processDefinitionId))
            .collect(Collectors.toSet());

    final Stream<String> definitionSequenceFlowIds =
        modelInstance.getModelElementsByType(SequenceFlow.class).stream()
            .filter(sequenceFlow -> definitionFlowNodes.contains(sequenceFlow.getSource()))
            .map(SequenceFlow::getId);

    return Stream.concat(
            definitionFlowNodes.stream().map(FlowNode::getId), definitionSequenceFlowIds)
        .collect(Collectors.toSet());
  }

  /**
   * Selects the model that describes more of the process out of two models sharing a process
   * definition id.
   *
   * <p>A process definition id can be deployed with different models within the same test run. The
   * most common case is a mocked child process: {@code MOCK_CHILD_PROCESS} deploys a stub of a few
   * elements under the id of the real process. The report describes such a process by its richest
   * model, so that the real BPMN wins over a stub.
   *
   * @param model A model of the process
   * @param otherModel Another model of the same process
   * @return The model with the higher element count, or the first one if both are equal
   */
  public static ProcessModel selectMostCompleteModel(
      final ProcessModel model, final ProcessModel otherModel) {
    return otherModel.getTotalElementCount() > model.getTotalElementCount() ? otherModel : model;
  }

  private static BpmnModelInstance readModel(final String xml, final String processDefinitionId) {
    final BpmnModelInstance modelInstance =
        Bpmn.readModelFromStream(new ByteArrayInputStream(xml.getBytes()));

    if (modelInstance == null) {
      throw new IllegalArgumentException(
          "Cannot read model from process definition: " + processDefinitionId);
    }
    return modelInstance;
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
