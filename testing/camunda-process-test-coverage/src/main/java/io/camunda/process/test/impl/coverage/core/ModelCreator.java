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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
   * The elements of a model, kept for as long as the model is measured against.
   *
   * <p>A suite reports itself after every test, and reporting measures every coverage of every run
   * it collected so far against the model it describes the process by. Reading the elements of that
   * model from its XML each time would parse it once per coverage, so the number of parses would
   * grow with the square of the tests in the suite. A model never changes, so its elements are read
   * once and shared from here. There is an entry per model the suite deployed, which the suite
   * holds on to anyway.
   */
  private static final Map<ProcessModel, CoverableElements> COVERABLE_ELEMENTS_BY_MODEL =
      new ConcurrentHashMap<>();

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
    return createModel(testResults, processDefinitionId, null);
  }

  /**
   * Creates a model object from a process definition in the Camunda engine.
   *
   * <p>A process definition id can be deployed several times within a test run, for example when a
   * mock deploys a stub of a process that is also deployed for real. The deployment that the
   * instance ran describes it; the other deployments describe a different process under the same
   * id, so no other deployment stands in for it.
   *
   * @param testResults The data source to retrieve process definition data
   * @param processDefinitionId The ID of the process definition to create a model for
   * @param processDefinitionKey The key of the deployment that ran, or {@code null} if unknown
   * @return A Model object containing process structure information and element counts
   * @throws IllegalArgumentException if the model cannot be read from the process definition
   */
  public static ProcessModel createModel(
      final CoverageTestData testResults,
      final String processDefinitionId,
      final Long processDefinitionKey) {

    final CoverageProcessDefinitionData processDefinitionData =
        selectDeployment(testResults, processDefinitionId, processDefinitionKey)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "No process definition data found for ID: "
                            + processDefinitionId
                            + (processDefinitionKey == null
                                ? ""
                                : " deployed under key: " + processDefinitionKey)));

    final BpmnModelInstance modelInstance =
        readModel(processDefinitionData.getXml(), processDefinitionId);

    final ProcessDefinition processDefinition = processDefinitionData.getProcessDefinition();

    return ImmutableProcessModel.builder()
        .processDefinitionId(processDefinition.getProcessDefinitionId())
        .processName(processDefinition.getName())
        .totalElementCount(
            coverableElements(modelInstance, processDefinition.getProcessDefinitionId()).count())
        .version(String.valueOf(processDefinition.getVersion()))
        .xml(Bpmn.convertToString(modelInstance))
        .build();
  }

  /**
   * Selects the deployment that the instance ran, out of the deployments of a process definition
   * id.
   *
   * <p>Another deployment of the id does not stand in for it: it describes a different process, so
   * its model would neither explain what the instance ran nor let its elements count as coverage.
   *
   * @param testResults The data source to retrieve process definition data
   * @param processDefinitionId The ID of the process definition
   * @param processDefinitionKey The key of the deployment that ran, or {@code null} if unknown
   * @return The deployment that ran, or any deployment of the id when the key is unknown
   */
  private static Optional<CoverageProcessDefinitionData> selectDeployment(
      final CoverageTestData testResults,
      final String processDefinitionId,
      final Long processDefinitionKey) {

    final Stream<CoverageProcessDefinitionData> deploymentsOfId =
        testResults.getProcessDefinitionData().stream()
            .filter(
                data ->
                    data.getProcessDefinition()
                        .getProcessDefinitionId()
                        .equals(processDefinitionId));

    if (processDefinitionKey == null) {
      return deploymentsOfId.findFirst();
    }

    return deploymentsOfId
        .filter(
            data ->
                processDefinitionKey.equals(data.getProcessDefinition().getProcessDefinitionKey()))
        .findFirst();
  }

  /**
   * Collects the elements a model can cover, which are the elements counted by {@link
   * #createModel}.
   *
   * @param processModel The model to collect the elements of
   * @return The flow nodes and sequence flows of the executable process, by kind
   */
  public static CoverableElements coverableElements(final ProcessModel processModel) {
    return COVERABLE_ELEMENTS_BY_MODEL.computeIfAbsent(
        processModel,
        model ->
            coverableElements(
                readModel(model.getXml(), model.getProcessDefinitionId()),
                model.getProcessDefinitionId()));
  }

  /**
   * Collects the elements a model can cover, which are the elements counted by {@link
   * #createModel}.
   *
   * @param modelInstance The parsed BPMN model
   * @param processDefinitionId The ID of the executable process within the model
   * @return The flow nodes and sequence flows of the executable process, by kind
   */
  public static CoverableElements coverableElements(
      final BpmnModelInstance modelInstance, final String processDefinitionId) {

    final Set<FlowNode> definitionFlowNodes =
        modelInstance.getModelElementsByType(FlowNode.class).stream()
            .filter(node -> isExecutable(node, processDefinitionId))
            .collect(Collectors.toSet());

    final Set<String> definitionSequenceFlowIds =
        modelInstance.getModelElementsByType(SequenceFlow.class).stream()
            .filter(sequenceFlow -> definitionFlowNodes.contains(sequenceFlow.getSource()))
            .map(SequenceFlow::getId)
            .collect(Collectors.toSet());

    return new CoverableElements(
        definitionFlowNodes.stream().map(FlowNode::getId).collect(Collectors.toSet()),
        definitionSequenceFlowIds);
  }

  /**
   * Selects the model that describes more of the process out of two models sharing a process
   * definition id.
   *
   * <p>A process definition id can be deployed with different models within the same suite, for
   * example when tests use fixtures that differ in their elements. The report describes such a
   * process by its richest model, so that the elements of the other models cannot count as coverage
   * of a model that does not have them.
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
