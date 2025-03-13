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

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.SearchRequestPage;
import io.camunda.client.api.search.filter.FlownodeInstanceFilter;
import io.camunda.client.api.search.filter.IncidentFilter;
import io.camunda.client.api.search.filter.ProcessInstanceFilter;
import io.camunda.client.api.search.response.FlowNodeInstance;
import io.camunda.client.api.search.response.Incident;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.Variable;
import java.util.List;
import java.util.function.Consumer;

public class CamundaDataSource {

  private static final Consumer<SearchRequestPage> DEFAULT_PAGE_REQUEST = page -> page.limit(100);

  private final CamundaClient client;

  public CamundaDataSource(final CamundaClient client) {
    this.client = client;
  }

  public List<FlowNodeInstance> findFlowNodeInstancesByProcessInstanceKey(
      final long processInstanceKey) {
    return findFlowNodeInstances(filter -> filter.processInstanceKey(processInstanceKey));
  }

  public List<FlowNodeInstance> findFlowNodeInstances(
      final Consumer<FlownodeInstanceFilter> filter) {
    return client
        .newFlownodeInstanceQuery()
        .filter(filter)
        .sort(sort -> sort.startDate().asc())
        .page(DEFAULT_PAGE_REQUEST)
        .send()
        .join()
        .items();
  }

  public List<Variable> findVariablesByProcessInstanceKey(final long processInstanceKey) {
    return client
        .newVariableQuery()
        .filter(filter -> filter.processInstanceKey(processInstanceKey))
        .page(DEFAULT_PAGE_REQUEST)
        .send()
        .join()
        .items();
  }

  public List<ProcessInstance> findProcessInstances() {
    return findProcessInstances(filter -> {});
  }

  public List<ProcessInstance> findProcessInstances(final Consumer<ProcessInstanceFilter> filter) {
    return client
        .newProcessInstanceQuery()
        .filter(filter)
        .sort(sort -> sort.startDate().asc())
        .page(DEFAULT_PAGE_REQUEST)
        .send()
        .join()
        .items();
  }

  public List<Incident> findIncidents(final Consumer<IncidentFilter> filter) {
    return client
        .newIncidentQuery()
        .filter(filter)
        .sort(sort -> sort.creationTime().asc())
        .page(DEFAULT_PAGE_REQUEST)
        .send()
        .join()
        .items();
  }
}
