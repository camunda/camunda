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

import io.camunda.client.api.search.enums.ElementInstanceType;
import io.camunda.client.api.search.response.ElementInstance;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.ProcessInstanceSequenceFlow;
import io.camunda.process.test.api.coverage.model.Coverage;
import io.camunda.process.test.api.coverage.model.Model;
import io.camunda.process.test.impl.assertions.CamundaDataSource;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.SequenceFlow;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Utility class for creating and aggregating process coverage data.
 *
 * <p>This class provides functionality to generate coverage metrics for BPMN processes by analyzing
 * process instances, element instances, and sequence flows. It can create individual coverage
 * reports for process instances and aggregate multiple coverage reports into consolidated results.
 */
public class CoverageCreator {
  /**
   * Creates a coverage report for a single process instance.
   *
   * <p>Analyzes the elements and sequence flows taken in the process instance and calculates the
   * overall coverage percentage based on the model definition.
   *
   * @param dataSource The data source to retrieve process execution data
   * @param processInstance The process instance to analyze
   * @param model The process model containing definition information
   * @return A Coverage object containing the coverage details for the process instance
   */
  public static Coverage createCoverage(
      final CamundaDataSource dataSource,
      final ProcessInstance processInstance,
      final Model model) {

    final List<ElementInstance> takenElementInstances =
        dataSource
            .findElementInstancesByProcessInstanceKey(processInstance.getProcessInstanceKey())
            .stream()
            .filter(node -> node.getType() != ElementInstanceType.PROCESS)
            .collect(Collectors.toList());

    final List<String> takenElements =
        takenElementInstances.stream()
            .map(ElementInstance::getElementId)
            .distinct()
            .collect(Collectors.toList());

    final List<String> takenSequenceFlows =
        dataSource
            .findSequenceFlowsByProcessInstanceKey(processInstance.getProcessInstanceKey())
            .stream()
            .map(ProcessInstanceSequenceFlow::getElementId)
            .distinct()
            .collect(Collectors.toList());

    // for event based gateways we need to find out how the flow continues to get the correct
    // sequence flow and add that as an event, as sequence flows after an event based gateway are
    // not reflected in the records
    enhanceSequenceFlowsByEventBasedGateway(takenSequenceFlows, takenElementInstances, model);

    return new Coverage(
        processInstance.getProcessDefinitionId(),
        takenElements,
        takenSequenceFlows,
        calculateCoverage(takenElements, takenSequenceFlows, model));
  }

  /**
   * Aggregates multiple coverage reports into consolidated reports per process definition.
   *
   * <p>Combines coverage data from multiple executions of the same process definition, ensuring
   * elements and sequence flows are counted only once in the aggregated result.
   *
   * @param coverages Collection of individual coverage reports to aggregate
   * @param models Collection of process models for coverage calculation
   * @return List of aggregated Coverage objects, one per process definition
   */
  public static List<Coverage> aggregateCoverages(
      final Collection<Coverage> coverages, final Collection<Model> models) {
    final Map<String, List<Coverage>> coveragesByProcessDefinition =
        coverages.stream().collect(Collectors.groupingBy(Coverage::getProcessDefinitionId));
    final List<Coverage> aggregatedCoverages = new ArrayList<>();
    coveragesByProcessDefinition.forEach(
        (processDefinitionId, coveragesForProcessInstance) -> {
          final List<String> completedElements =
              coveragesForProcessInstance.stream()
                  .flatMap(c -> c.getCompletedElements().stream())
                  .distinct()
                  .collect(Collectors.toList());
          final List<String> takenSequenceFlows =
              coveragesForProcessInstance.stream()
                  .flatMap(c -> c.getTakenSequenceFlows().stream())
                  .distinct()
                  .collect(Collectors.toList());
          final Model model =
              models.stream()
                  .filter(m -> m.getProcessDefinitionId().equals(processDefinitionId))
                  .findFirst()
                  .orElseThrow(
                      () ->
                          new IllegalStateException(
                              "No model found for process definition id: " + processDefinitionId));
          aggregatedCoverages.add(
              new Coverage(
                  processDefinitionId,
                  completedElements,
                  takenSequenceFlows,
                  calculateCoverage(completedElements, takenSequenceFlows, model)));
        });
    return aggregatedCoverages;
  }

  /**
   * Enhances sequence flow coverage data by analyzing event-based gateways.
   *
   * <p>Since sequence flows after event-based gateways may not be directly recorded in execution,
   * this method identifies and adds missing sequence flows based on the process structure.
   *
   * @param takenSequenceFlows List of sequence flow IDs to enhance
   * @param takenNodeElements List of element instances from the process execution
   * @param model The process model containing definition information
   */
  private static void enhanceSequenceFlowsByEventBasedGateway(
      final List<String> takenSequenceFlows,
      final List<ElementInstance> takenNodeElements,
      final Model model) {
    final BpmnModelInstance modelInstance =
        Bpmn.readModelFromStream(new ByteArrayInputStream(model.getXml().getBytes()));

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
   * @param model The process model containing definition information
   * @return Coverage percentage as a value between 0.0 and 1.0
   */
  private static double calculateCoverage(
      final List<String> takenElements, final List<String> takenSequenceFlows, final Model model) {
    if (model.getTotalElementCount() == 0) {
      return 0.0;
    }
    final double coveredElements = takenElements.size() + takenSequenceFlows.size();
    return coveredElements / model.getTotalElementCount();
  }
}
