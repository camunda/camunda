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

import io.camunda.client.api.search.enums.ElementInstanceState;
import io.camunda.client.api.search.enums.ElementInstanceType;
import io.camunda.client.api.search.response.ElementInstance;
import io.camunda.client.api.search.response.ProcessInstanceSequenceFlow;
import io.camunda.process.test.api.coverage.model.ImmutableProcessCoverage;
import io.camunda.process.test.api.coverage.model.ProcessCoverage;
import io.camunda.process.test.api.coverage.model.ProcessModel;
import io.camunda.process.test.impl.coverage.data.CoverageProcessInstanceData;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.SequenceFlow;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Utility class for creating and aggregating process coverage data.
 *
 * <p>This class provides functionality to generate coverage metrics for BPMN processes by analyzing
 * process instances, element instances, and sequence flows. It can create individual coverage
 * reports for process instances and aggregate multiple coverage reports into consolidated results.
 */
public class CoverageCreator {

  private static final EnumSet<ElementInstanceType> EXCLUDED_ELEMENT_TYPES =
      EnumSet.of(
          ElementInstanceType.PROCESS, ElementInstanceType.AD_HOC_SUB_PROCESS_INNER_INSTANCE);

  /**
   * Creates a coverage report for a single process instance.
   *
   * <p>Analyzes the elements and sequence flows taken in the process instance and calculates the
   * overall coverage percentage based on the model definition. Elements that the model does not
   * contain are not covered by it and are left out.
   *
   * @param processInstanceData The process instance to analyze
   * @param processModel The process model containing definition information
   * @return A ProcessCoverage object containing the coverage details for the process instance
   */
  public static ProcessCoverage createCoverage(
      final CoverageProcessInstanceData processInstanceData, final ProcessModel processModel) {

    final List<ElementInstance> completedElementInstances =
        processInstanceData.getElementInstances().stream()
            .filter(elementInstance -> !EXCLUDED_ELEMENT_TYPES.contains(elementInstance.getType()))
            .filter(elementInstance -> elementInstance.getState() == ElementInstanceState.COMPLETED)
            .collect(Collectors.toList());

    final List<String> completedElementIds =
        completedElementInstances.stream()
            .map(ElementInstance::getElementId)
            .distinct()
            .collect(Collectors.toList());

    final List<String> takenSequenceFlowIds =
        processInstanceData.getSequenceFlows().stream()
            .map(ProcessInstanceSequenceFlow::getElementId)
            .distinct()
            .collect(Collectors.toList());

    final BpmnModelInstance modelInstance =
        Bpmn.readModelFromStream(new ByteArrayInputStream(processModel.getXml().getBytes()));

    // for event based gateways we need to find out how the flow continues to get the correct
    // sequence flow and add that as an event, as sequence flows after an event based gateway are
    // not reflected in the records
    enhanceSequenceFlowsByEventBasedGateway(
        takenSequenceFlowIds, completedElementInstances, modelInstance);

    final CoverableElements coverableElements =
        ModelCreator.coverableElements(modelInstance, processModel.getProcessDefinitionId());
    final List<String> coveredElements =
        retainCoverable(completedElementIds, coverableElements.getFlowNodeIds());
    final List<String> coveredSequenceFlows =
        retainCoverable(takenSequenceFlowIds, coverableElements.getSequenceFlowIds());

    return ImmutableProcessCoverage.builder()
        .processDefinitionId(processInstanceData.getProcessInstance().getProcessDefinitionId())
        .addAllCompletedElements(coveredElements)
        .addAllTakenSequenceFlows(coveredSequenceFlows)
        .coverage(calculateCoverage(coveredElements, coveredSequenceFlows, processModel))
        .build();
  }

  /**
   * Aggregates multiple coverage reports into consolidated reports per process definition.
   *
   * <p>Combines coverage data from multiple executions of the same process definition, ensuring
   * elements and sequence flows are counted only once in the aggregated result. Elements that the
   * reported model does not contain are not covered by it and are left out.
   *
   * @param coverages Collection of individual coverage reports to aggregate
   * @param processModels Collection of process models for coverage calculation
   * @return List of aggregated ProcessCoverage objects, one per process definition
   */
  public static List<ProcessCoverage> aggregateCoverages(
      final Collection<ProcessCoverage> coverages, final Collection<ProcessModel> processModels) {
    final Map<String, List<ProcessCoverage>> coveragesByProcessDefinition =
        coverages.stream().collect(Collectors.groupingBy(ProcessCoverage::getProcessDefinitionId));
    final List<ProcessCoverage> aggregatedCoverages = new ArrayList<>();
    coveragesByProcessDefinition.forEach(
        (processDefinitionId, coveragesForProcessInstance) ->
            aggregatedCoverages.add(
                aggregate(processDefinitionId, coveragesForProcessInstance, processModels)));
    return aggregatedCoverages;
  }

  /**
   * Measures a coverage against the model the report describes its process by.
   *
   * <p>A coverage is collected against the deployment the process instance ran, which is not
   * necessarily the deployment the report describes the process by: a process can be deployed both
   * for real and as a stub that {@code MOCK_CHILD_PROCESS} deploys under the same id. The report
   * renders the coverage against the model it selected, so elements of another deployment neither
   * count towards the percentage nor are highlighted in that diagram.
   *
   * @param coverage The coverage as it was collected
   * @param processModels The models the report describes the processes by
   * @return The coverage, measured against the model of its process definition id
   */
  public static ProcessCoverage measureAgainstReportedModel(
      final ProcessCoverage coverage, final Collection<ProcessModel> processModels) {
    return aggregate(
        coverage.getProcessDefinitionId(), Collections.singletonList(coverage), processModels);
  }

  private static ProcessCoverage aggregate(
      final String processDefinitionId,
      final List<ProcessCoverage> coverages,
      final Collection<ProcessModel> processModels) {
    final ProcessModel processModel =
        processModels.stream()
            .filter(m -> m.getProcessDefinitionId().equals(processDefinitionId))
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "No model found for process definition id: " + processDefinitionId));
    final CoverableElements coverableElements = ModelCreator.coverableElements(processModel);
    final List<String> completedElements =
        retainCoverable(
            coverages.stream()
                .flatMap(c -> c.getCompletedElements().stream())
                .distinct()
                .collect(Collectors.toList()),
            coverableElements.getFlowNodeIds());
    final List<String> takenSequenceFlows =
        retainCoverable(
            coverages.stream()
                .flatMap(c -> c.getTakenSequenceFlows().stream())
                .distinct()
                .collect(Collectors.toList()),
            coverableElements.getSequenceFlowIds());
    return ImmutableProcessCoverage.builder()
        .processDefinitionId(processDefinitionId)
        .addAllCompletedElements(completedElements)
        .addAllTakenSequenceFlows(takenSequenceFlows)
        .coverage(calculateCoverage(completedElements, takenSequenceFlows, processModel))
        .build();
  }

  /**
   * Retains the elements that the reported model can cover as the kind of element they are.
   *
   * <p>A process definition id can be covered by more than one model, for example by the real
   * process and by the stub that {@code MOCK_CHILD_PROCESS} deploys under the same id. The report
   * describes such a process by a single model, so elements of the other models are not part of the
   * coverage: they are neither counted nor highlighted in the diagram. An id names an element only
   * within its own model, so what an instance completed is retained against the flow nodes of the
   * reported model and what it took against its sequence flows. Retaining both against all of its
   * elements would let one element count twice.
   *
   * <p>An element that an instance covered several times, for example by looping over it, is
   * retained once. Otherwise it would count as covering several elements of the model.
   *
   * @param elementIds The ids of the covered elements
   * @param coverableElementIds The ids of the elements of the reported model of that kind
   * @return The distinct covered element ids that are part of the reported model
   */
  private static List<String> retainCoverable(
      final List<String> elementIds, final Set<String> coverableElementIds) {
    return elementIds.stream()
        .filter(coverableElementIds::contains)
        .distinct()
        .collect(Collectors.toList());
  }

  /**
   * Enhances sequence flow coverage data by analyzing event-based gateways.
   *
   * <p>Since sequence flows after event-based gateways may not be directly recorded in execution,
   * this method identifies and adds missing sequence flows based on the process structure.
   *
   * @param takenSequenceFlows List of sequence flow IDs to enhance
   * @param takenNodeElements List of element instances from the process execution
   * @param modelInstance The parsed BPMN model of the process
   */
  private static void enhanceSequenceFlowsByEventBasedGateway(
      final List<String> takenSequenceFlows,
      final List<ElementInstance> takenNodeElements,
      final BpmnModelInstance modelInstance) {

    for (int i = 0; i < takenNodeElements.size(); i++) {
      final ElementInstance event = takenNodeElements.get(i);
      if (event.getType() == ElementInstanceType.EVENT_BASED_GATEWAY) {
        // If event based gateway is found, get the model and find outgoing flows
        final List<SequenceFlow> outgoingFlows =
            modelInstance.getModelElementsByType(SequenceFlow.class).stream()
                .filter(flow -> flow.getSource().getId().equals(event.getElementId()))
                .collect(Collectors.toList());

        // Check remaining events to find matching event
        for (int j = i + 1; j < takenNodeElements.size(); j++) {
          final ElementInstance nextEvent = takenNodeElements.get(j);
          outgoingFlows.stream()
              .filter(flow -> flow.getTarget().getId().equals(nextEvent.getElementId()))
              .map(SequenceFlow::getId)
              .findFirst()
              .ifPresent(takenSequenceFlows::add);
        }
      }
    }
  }

  /**
   * Calculates the coverage percentage for a process instance.
   *
   * <p>Determines what percentage of the total model elements were executed by comparing taken
   * elements and sequence flows against the model definition.
   *
   * @param takenElements List of element IDs that were executed
   * @param takenSequenceFlows List of sequence flow IDs that were traversed
   * @param processModel The process model containing definition information
   * @return ProcessCoverage percentage as a value between 0.0 and 1.0
   */
  private static double calculateCoverage(
      final List<String> takenElements,
      final List<String> takenSequenceFlows,
      final ProcessModel processModel) {
    if (processModel.getTotalElementCount() == 0) {
      return 0.0;
    }
    final double coveredElements = takenElements.size() + takenSequenceFlows.size();
    return coveredElements / processModel.getTotalElementCount();
  }
}
