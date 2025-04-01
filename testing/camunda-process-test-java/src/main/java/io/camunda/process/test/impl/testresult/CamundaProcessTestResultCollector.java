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

import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.search.enums.FlowNodeInstanceState;
import io.camunda.client.api.search.enums.IncidentState;
import io.camunda.client.api.search.response.FlowNodeInstance;
import io.camunda.client.api.search.response.Incident;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.Variable;
import io.camunda.process.test.impl.assertions.CamundaDataSource;
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
    } catch (final ClientException e) {
      LOG.warn("Failed to collect the process instance results.", e);
    }

    return result;
  }

  private ProcessInstanceResult collectProcessInstanceResult(
      final ProcessInstance processInstance) {
    final ProcessInstanceResult result = new ProcessInstanceResult();

    final long processInstanceKey = processInstance.getProcessInstanceKey();

    result.setProcessInstance(processInstance);
    result.setVariables(collectVariables(processInstanceKey));
    result.setOpenIncidents(collectOpenIncidents(processInstanceKey));
    result.setActiveFlowNodeInstances(collectActiveFlowNodeInstances(processInstanceKey));

    return result;
  }

  private Map<String, String> collectVariables(final long processInstanceKey) {
    return dataSource.findVariablesByProcessInstanceKey(processInstanceKey).stream()
        .collect(Collectors.toMap(Variable::getName, Variable::getValue));
  }

  private List<Incident> collectOpenIncidents(final long processInstanceKey) {
    return dataSource.findIncidents(
        filter -> filter.processInstanceKey(processInstanceKey).state(IncidentState.ACTIVE));
  }

  private List<FlowNodeInstance> collectActiveFlowNodeInstances(final long processInstanceKey) {
    return dataSource.findFlowNodeInstances(
        filter ->
            filter.processInstanceKey(processInstanceKey).state(FlowNodeInstanceState.ACTIVE));
  }
}
