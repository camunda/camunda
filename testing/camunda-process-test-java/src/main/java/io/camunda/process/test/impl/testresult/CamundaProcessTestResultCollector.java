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
import io.camunda.client.api.search.response.FlowNodeInstanceState;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.process.test.impl.assertions.CamundaDataSource;
import io.camunda.process.test.impl.client.IncidentDto;
import io.camunda.process.test.impl.client.VariableDto;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CamundaProcessTestResultCollector {

  private static final Logger LOG =
      LoggerFactory.getLogger(CamundaProcessTestResultCollector.class);

  private final CamundaDataSource dataSource;

  public CamundaProcessTestResultCollector(final CamundaDataSource dataSource) {
    this.dataSource = dataSource;
  }

  public ProcessTestResult collect() {
    final ProcessTestResult result = new ProcessTestResult();

    try {
      final List<ProcessInstanceResult> processInstanceResults =
          dataSource.findProcessInstances().stream()
              .map(this::collectProcessInstanceResult)
              .collect(Collectors.toList());
      result.setProcessInstanceTestResults(processInstanceResults);
    } catch (final IOException e) {
      LOG.warn("Failed to collect the process instance results.", e);
    }

    return result;
  }

  private ProcessInstanceResult collectProcessInstanceResult(
      final ProcessInstance processInstance) {
    final ProcessInstanceResult result = new ProcessInstanceResult();

    final long processInstanceKey = processInstance.getProcessInstanceKey();

    result.setProcessInstanceKey(processInstanceKey);
    result.setProcessId(processInstance.getProcessDefinitionId());
    result.setVariables(collectVariables(processInstanceKey));
    result.setOpenIncidents(collectOpenIncidents(processInstanceKey));
    result.setActiveFlowNodeInstances(collectActiveFlowNodeInstances(processInstanceKey));

    return result;
  }

  private Map<String, String> collectVariables(final long processInstanceKey) {
    try {
      return dataSource.getVariablesByProcessInstanceKey(processInstanceKey).stream()
          .collect(Collectors.toMap(VariableDto::getName, VariableDto::getValue));
    } catch (final IOException e) {
      LOG.warn("Failed to collect process instance variables for key '{}'", processInstanceKey, e);
    }
    return Collections.emptyMap();
  }

  private List<OpenIncident> collectOpenIncidents(final long processInstanceKey) {
    try {
      return dataSource.getFlowNodeInstancesByProcessInstanceKey(processInstanceKey).stream()
          .filter(FlowNodeInstance::getIncident)
          .map(this::getIncident)
          .collect(Collectors.toList());
    } catch (final IOException e) {
      LOG.warn(
          "Failed to collect incidents for process instance with key '{}'", processInstanceKey, e);
    }
    return Collections.emptyList();
  }

  private OpenIncident getIncident(final FlowNodeInstance flowNodeInstance) {
    final OpenIncident openIncident = new OpenIncident();
    openIncident.setFlowNodeId(flowNodeInstance.getFlowNodeId());

    try {
      final IncidentDto incident = dataSource.getIncidentByKey(flowNodeInstance.getIncidentKey());
      openIncident.setType(incident.getType());
      openIncident.setMessage(incident.getMessage());

    } catch (final IOException e) {
      openIncident.setType("?");
      openIncident.setMessage("?");
    }
    return openIncident;
  }

  private List<FlowNodeInstance> collectActiveFlowNodeInstances(final long processInstanceKey) {
    try {
      return dataSource.getFlowNodeInstancesByProcessInstanceKey(processInstanceKey).stream()
          .filter(
              flowNodeInstance -> flowNodeInstance.getState().equals(FlowNodeInstanceState.ACTIVE))
          .collect(Collectors.toList());
    } catch (final IOException e) {
      LOG.warn(
          "Failed to collect flow-node instances for process instance with key '{}'",
          processInstanceKey,
          e);
    }
    return Collections.emptyList();
  }
}
