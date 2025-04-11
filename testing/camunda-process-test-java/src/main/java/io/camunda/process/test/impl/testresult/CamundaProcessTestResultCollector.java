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

import io.camunda.client.api.search.enums.ElementInstanceState;
import io.camunda.client.api.search.enums.IncidentState;
import io.camunda.client.api.search.response.ElementInstance;
import io.camunda.client.api.search.response.Incident;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.process.test.impl.assertions.CamundaDataSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CamundaProcessTestResultCollector {

  private final CamundaDataSource dataSource;

  public CamundaProcessTestResultCollector(final CamundaDataSource dataSource) {
    this.dataSource = dataSource;
  }

  public ProcessTestResult collect() {
    final ProcessTestResult result = new ProcessTestResult();

    final List<ProcessInstanceResult> processInstanceResults =
        dataSource.findProcessInstances().stream()
            .map(this::collectProcessInstanceResult)
            .collect(Collectors.toList());
    result.setProcessInstanceTestResults(processInstanceResults);

    return result;
  }

  private ProcessInstanceResult collectProcessInstanceResult(
      final ProcessInstance processInstance) {
    final ProcessInstanceResult result = new ProcessInstanceResult();

    final long processInstanceKey = processInstance.getProcessInstanceKey();

    result.setProcessInstance(processInstance);
    result.setVariables(collectVariables(processInstanceKey));
    result.setOpenIncidents(collectOpenIncidents(processInstanceKey));
    result.setActiveElementInstances(collectActiveElementInstances(processInstanceKey));

    return result;
  }

  private Map<String, String> collectVariables(final long processInstanceKey) {
    return dataSource.findVariablesByProcessInstanceKey(processInstanceKey).stream()
        // We're deliberately switching from the Collectors.toMap collector to a custom
        // implementation because it's allowed to have Camunda Variables with null values
        // However, the toMap collector does not allow null values and would throw an exception.
        // See this Stack Overflow issue for more context: https://stackoverflow.com/a/24634007
        .collect(HashMap::new, (m, v) -> m.put(v.getName(), v.getValue()), HashMap::putAll);
  }

  private List<Incident> collectOpenIncidents(final long processInstanceKey) {
    return dataSource.findIncidents(
        filter -> filter.processInstanceKey(processInstanceKey).state(IncidentState.ACTIVE));
  }

  private List<ElementInstance> collectActiveElementInstances(final long processInstanceKey) {
    return dataSource.findElementInstances(
        filter ->
            filter.processInstanceKey(processInstanceKey).state(ElementInstanceState.ACTIVE));
  }
}
