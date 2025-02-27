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
package io.camunda.process.test.impl.testresult;

import io.camunda.client.api.search.response.FlowNodeInstance;
import io.camunda.client.api.search.response.Incident;
import io.camunda.client.api.search.response.ProcessInstance;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

public class CamundaProcessTestResultPrinter {

  private static final String NO_ENTRIES = "<None>";
  private static final int MAX_VALUE_LENGTH = 500;

  private final Consumer<String> printStream;

  public CamundaProcessTestResultPrinter(final Consumer<String> printStream) {
    this.printStream = printStream;
  }

  public void print(final ProcessTestResult result) {
    final String formattedResult = formatResult(result);
    printStream.accept(formattedResult);
  }

  private String formatResult(final ProcessTestResult result) {
    return "Process test results:\n"
        + "=====================\n\n"
        + formatProcessInstances(result.getProcessInstanceTestResults())
        + "\n"
        + "=====================\n";
  }

  private static String formatProcessInstances(
      final List<ProcessInstanceResult> processInstanceResults) {
    return processInstanceResults.stream()
        .map(CamundaProcessTestResultPrinter::formatProcessInstance)
        .collect(Collectors.joining("\n---------------------\n\n"));
  }

  private static String formatProcessInstance(final ProcessInstanceResult result) {
    final ProcessInstance processInstance = result.getProcessInstance();

    final String formattedProcessInstance =
        String.format(
            "Process instance: %d [process-id: '%s']",
            processInstance.getProcessInstanceKey(), processInstance.getProcessDefinitionId());

    return formattedProcessInstance
        + "\n\n"
        + "Active elements:\n"
        + formatFlowNodeInstances(result.getActiveFlowNodeInstances())
        + "\n\n"
        + "Variables:\n"
        + formatVariables(result.getVariables())
        + "\n\n"
        + "Open incidents:\n"
        + formatIncidents(result.getOpenIncidents());
  }

  private static String formatVariables(final Map<String, String> variables) {
    if (variables.isEmpty()) {
      return NO_ENTRIES;
    } else {
      return variables.entrySet().stream()
          .map(variable -> formatVariable(variable.getKey(), variable.getValue()))
          .collect(Collectors.joining("\n"));
    }
  }

  private static String formatVariable(final String key, final String value) {
    return String.format("- '%s': %s", key, abbreviate(value));
  }

  private static String abbreviate(final String value) {
    return StringUtils.abbreviate(value, MAX_VALUE_LENGTH);
  }

  private static String formatIncidents(final List<Incident> incidents) {
    if (incidents.isEmpty()) {
      return NO_ENTRIES;
    } else {
      return incidents.stream()
          .map(CamundaProcessTestResultPrinter::formatIncident)
          .collect(Collectors.joining("\n"));
    }
  }

  private static String formatIncident(final Incident incident) {
    return String.format(
        "- '%s' [type: %s] \"%s\"",
        incident.getFlowNodeId(), incident.getErrorType(), abbreviate(incident.getErrorMessage()));
  }

  private static String formatFlowNodeInstances(final List<FlowNodeInstance> flowNodeInstances) {
    if (flowNodeInstances.isEmpty()) {
      return NO_ENTRIES;
    } else {
      return flowNodeInstances.stream()
          .map(CamundaProcessTestResultPrinter::formatFlowNodeInstance)
          .collect(Collectors.joining("\n"));
    }
  }

  private static String formatFlowNodeInstance(final FlowNodeInstance flowNodeInstance) {
    final String name = Optional.ofNullable(flowNodeInstance.getFlowNodeName()).orElse("");
    return String.format("- '%s' [name: '%s']", flowNodeInstance.getFlowNodeId(), name);
  }
}
