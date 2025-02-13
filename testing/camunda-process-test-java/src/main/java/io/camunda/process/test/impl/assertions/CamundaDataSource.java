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
package io.camunda.process.test.impl.assertions;

import io.camunda.process.test.impl.client.CamundaApiClient;
import io.camunda.process.test.impl.client.FlowNodeInstanceDto;
import io.camunda.process.test.impl.client.IncidentDto;
import io.camunda.process.test.impl.client.ProcessInstanceDto;
import io.camunda.process.test.impl.client.VariableDto;
import io.camunda.zeebe.client.api.search.response.ProcessInstance;
import java.io.IOException;
import java.util.List;

public class CamundaDataSource {

  private final CamundaApiClient camundaApiClient;

  public CamundaDataSource(final String camundaApiEndpoint) {
    camundaApiClient = new CamundaApiClient(camundaApiEndpoint);
  }

  public ProcessInstanceDto getProcessInstance(final long processInstanceKey) throws IOException {
    return camundaApiClient.getProcessInstanceByKey(processInstanceKey);
  }

  public List<FlowNodeInstanceDto> getFlowNodeInstancesByProcessInstanceKey(
      final long processInstanceKey) throws IOException {
    return camundaApiClient
        .findFlowNodeInstancesByProcessInstanceKey(processInstanceKey)
        .getItems();
  }

  public List<VariableDto> getVariablesByProcessInstanceKey(final long processInstanceKey)
      throws IOException {
    return camundaApiClient.findVariablesByProcessInstanceKey(processInstanceKey).getItems();
  }

  public List<ProcessInstance> findProcessInstances() throws IOException {
    return camundaApiClient.findProcessInstances().getItems();
  }

  public IncidentDto getIncidentByKey(final long incidentKey) throws IOException {
    return camundaApiClient.getIncidentByKey(incidentKey);
  }
}
