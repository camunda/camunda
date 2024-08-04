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

import io.camunda.process.test.impl.client.FlowNodeInstanceDto;
import io.camunda.process.test.impl.client.OperateApiClient;
import io.camunda.process.test.impl.client.ProcessInstanceDto;
import java.io.IOException;
import java.util.List;

public class CamundaDataSource {

  private final OperateApiClient operateApiClient;

  public CamundaDataSource(final String operateApiEndpoint) {
    operateApiClient = new OperateApiClient(operateApiEndpoint);
  }

  public ProcessInstanceDto getProcessInstance(final long processInstanceKey) throws IOException {
    return operateApiClient.getProcessInstanceByKey(processInstanceKey);
  }

  public List<FlowNodeInstanceDto> getFlowNodeInstancesByProcessInstanceKey(
      final long processInstanceKey) throws IOException {
    return operateApiClient
        .findFlowNodeInstancesByProcessInstanceKey(processInstanceKey)
        .getItems();
  }
}
